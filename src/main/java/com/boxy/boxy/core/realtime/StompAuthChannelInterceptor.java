package com.boxy.boxy.core.realtime;

import com.boxy.boxy.core.security.CustomUserDetailsService;
import com.boxy.boxy.core.security.JwtTokenProvider;
import com.boxy.boxy.core.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The WebSocket's only line of defense, sitting on the inbound STOMP channel. A browser can't set
 * an {@code Authorization} header on the WebSocket handshake, so the HTTP handshake is anonymous
 * (see {@code SecurityConfig}) and authentication happens on the STOMP CONNECT frame instead:
 * <ul>
 *   <li><b>CONNECT</b> — the {@code Authorization: Bearer} access token is validated exactly as
 *   {@code JwtAuthFilter} does for REST (valid signature, user still exists and is active), then
 *   its tenant scope is attached as the session {@link RealtimePrincipal}.</li>
 *   <li><b>SUBSCRIBE</b> — only the company/branch topics the principal belongs to, plus the
 *   principal's own {@code /user/queue/notifications}. Anything else is refused, so one tenant can
 *   never listen to another's stock, sales or transfers.</li>
 *   <li><b>SEND</b> — always refused: the channel is server-to-client only.</li>
 * </ul>
 * Rejections are deliberately terse — no hint about which check failed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** Cap on SUBSCRIBE frames per socket. Legitimate clients hold a handful (RxStomp shares one
     *  STOMP subscription per destination), so this only bites a client trying to exhaust memory. */
    private static final int MAX_SUBSCRIPTIONS_PER_SESSION = 50;
    private static final String SUBSCRIPTION_COUNT_ATTR = "realtime.subscriptionCount";
    private static final String USER_NOTIFICATIONS_DESTINATION = "/user/queue/notifications";

    private static final Pattern COMPANY_TOPIC = Pattern.compile("^/topic/company\\.(\\d{1,18})\\.(stock|transfers)$");
    private static final Pattern BRANCH_TOPIC = Pattern.compile("^/topic/company\\.(\\d{1,18})\\.branch\\.(\\d{1,18})\\.sales$");

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final RealtimeSessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        StompCommand command = accessor != null ? accessor.getCommand() : null;
        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw reject("Publishing is not allowed");
            case DISCONNECT -> {
                // Always allowed — a client must be able to leave.
            }
            default -> requireAuthenticated(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            throw reject("Authentication required");
        }
        String jwt = header.substring(7);
        if (!tokenProvider.validateToken(jwt)) {
            throw reject("Authentication required");
        }

        UserDetails details;
        try {
            details = userDetailsService.loadUserById(tokenProvider.getUserIdFromToken(jwt));
        } catch (RuntimeException e) {
            log.warn("WebSocket CONNECT rejected: could not load user ({})", e.getMessage());
            throw reject("Authentication required");
        }
        if (!details.isEnabled() || !(details instanceof UserPrincipal principal) || principal.getCompanyId() == null) {
            throw reject("Authentication required");
        }

        accessor.setUser(new RealtimePrincipal(principal.getId(), principal.getCompanyId(), principal.getAssignedBranchIds()));
        Instant expiresAt = tokenProvider.getExpirationFromToken(jwt);
        if (accessor.getSessionId() != null) {
            sessionRegistry.authenticate(accessor.getSessionId(), expiresAt);
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        RealtimePrincipal principal = requireAuthenticated(accessor);
        String destination = accessor.getDestination();

        if (destination == null || !isAllowedDestination(principal, destination)) {
            log.warn("WebSocket SUBSCRIBE denied for user {} to '{}'", principal.userId(), destination);
            throw reject("Subscription not allowed");
        }

        // Fail closed: a WebSocket always has session attributes, so their absence means something is
        // wrong and the safe answer is to refuse rather than silently lose the cap.
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            throw reject("Subscription not allowed");
        }
        AtomicInteger count = (AtomicInteger) attributes.computeIfAbsent(SUBSCRIPTION_COUNT_ATTR, k -> new AtomicInteger());
        if (count.incrementAndGet() > MAX_SUBSCRIPTIONS_PER_SESSION) {
            throw reject("Too many subscriptions");
        }
    }

    private boolean isAllowedDestination(RealtimePrincipal principal, String destination) {
        if (USER_NOTIFICATIONS_DESTINATION.equals(destination)) {
            return true; // Spring resolves /user/... against this session's own principal name.
        }

        Matcher company = COMPANY_TOPIC.matcher(destination);
        if (company.matches()) {
            return principal.companyId().equals(Long.parseLong(company.group(1)));
        }

        Matcher branch = BRANCH_TOPIC.matcher(destination);
        if (branch.matches()) {
            return principal.companyId().equals(Long.parseLong(branch.group(1)))
                    && principal.hasBranch(Long.parseLong(branch.group(2)));
        }
        return false;
    }

    private RealtimePrincipal requireAuthenticated(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof RealtimePrincipal principal) {
            return principal;
        }
        throw reject("Authentication required");
    }

    private static MessageDeliveryException reject(String reason) {
        return new MessageDeliveryException(reason);
    }
}
