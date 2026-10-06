package com.boxy.boxy.core.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Periodically applies {@link RealtimeSessionRegistry#sweep} — see there for why. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionExpiry {

    private final RealtimeSessionRegistry registry;

    @Scheduled(fixedDelayString = "${app.realtime.sweep-interval-ms:10000}")
    public void closeExpiredSessions() {
        int closed = registry.sweep(Instant.now());
        if (closed > 0) {
            log.debug("Closed {} expired/unauthenticated WebSocket session(s)", closed);
        }
    }
}
