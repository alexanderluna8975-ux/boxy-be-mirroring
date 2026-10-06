package com.boxy.boxy.core.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks every open WebSocket so two things a stateless JWT can't do on its own are still
 * enforced:
 * <ul>
 *   <li>a socket is closed once the access token that authenticated it expires — otherwise a
 *   deactivated account or a changed password would keep receiving events for as long as the
 *   connection stayed open (the client simply reconnects with a fresh token);</li>
 *   <li>a socket that opens but never authenticates (the handshake itself is anonymous, see
 *   {@code SecurityConfig}) is dropped after a short grace period, so anonymous connections
 *   can't be parked on the server.</li>
 * </ul>
 * The STOMP session id and {@link WebSocketSession#getId()} are the same value, which is what
 * lets the CONNECT-frame interceptor tie a token expiry to the socket it arrived on.
 */
@Slf4j
@Component
public class RealtimeSessionRegistry {

    private record Tracked(WebSocketSession session, Instant openedAt, Instant expiresAt) {
        Tracked authenticatedUntil(Instant expiry) {
            return new Tracked(session, openedAt, expiry);
        }
    }

    private final Map<String, Tracked> sessions = new ConcurrentHashMap<>();
    private final Duration unauthenticatedTimeout;

    public RealtimeSessionRegistry(
            @Value("${app.realtime.unauthenticated-timeout-seconds:15}") long unauthenticatedTimeoutSeconds) {
        this.unauthenticatedTimeout = Duration.ofSeconds(unauthenticatedTimeoutSeconds);
    }

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), new Tracked(session, Instant.now(), null));
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
    }

    /** Called once the CONNECT frame's token is verified; {@code expiresAt} is that token's expiry. */
    public void authenticate(String sessionId, Instant expiresAt) {
        sessions.computeIfPresent(sessionId, (id, tracked) -> tracked.authenticatedUntil(expiresAt));
    }

    public int openSessionCount() {
        return sessions.size();
    }

    /** Closes every expired or never-authenticated socket; returns how many were closed. */
    public int sweep(Instant now) {
        int closed = 0;
        for (Map.Entry<String, Tracked> entry : sessions.entrySet()) {
            Tracked tracked = entry.getValue();
            boolean expired = tracked.expiresAt() != null && !tracked.expiresAt().isAfter(now);
            boolean neverAuthenticated = tracked.expiresAt() == null
                    && tracked.openedAt().plus(unauthenticatedTimeout).isBefore(now);

            if ((expired || neverAuthenticated) && sessions.remove(entry.getKey(), tracked)) {
                close(tracked.session(), expired ? "Token expired" : "Not authenticated");
                closed++;
            }
        }
        return closed;
    }

    private void close(WebSocketSession session, String reason) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
            }
        } catch (IOException e) {
            log.debug("Failed to close WebSocket session {}: {}", session.getId(), e.getMessage());
        }
    }
}
