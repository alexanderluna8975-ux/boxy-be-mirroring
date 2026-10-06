package com.boxy.boxy.core.realtime;

import com.boxy.boxy.core.security.CustomUserDetailsService;
import com.boxy.boxy.core.security.JwtTokenProvider;
import com.boxy.boxy.core.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The WebSocket has no other security layer: the HTTP handshake is anonymous, so everything a
 * tenant boundary depends on is decided here. These tests are that boundary's contract.
 */
@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    private static final long USER_ID = 7L;
    private static final long COMPANY = 4L;
    private static final long BRANCH_MINE = 10L;
    private static final long BRANCH_OTHER = 99L;

    @Mock private JwtTokenProvider tokenProvider;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private RealtimeSessionRegistry sessionRegistry;

    @InjectMocks private StompAuthChannelInterceptor interceptor;

    private static StompHeaderAccessor accessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static UserPrincipal principal(String status) {
        return UserPrincipal.create(USER_ID, COMPANY, "ana", "ana@boxy.dev", "hash", "Ana", BRANCH_MINE,
                status, null, List.of(), Set.of(BRANCH_MINE));
    }

    private StompHeaderAccessor connected() {
        StompHeaderAccessor connect = accessor(StompCommand.CONNECT);
        // One attributes map per socket, shared by every frame of that socket (as in production).
        // Set only here: Spring's setHeader ignores a value equal to the current one, so presetting
        // an empty map on each accessor would stop this shared one from being installed later.
        connect.setSessionAttributes(new HashMap<>());
        connect.setNativeHeader("Authorization", "Bearer good-token");
        when(tokenProvider.validateToken("good-token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("good-token")).thenReturn(USER_ID);
        when(tokenProvider.getExpirationFromToken("good-token")).thenReturn(Instant.now().plusSeconds(900));
        when(userDetailsService.loadUserById(USER_ID)).thenReturn(principal("ACTIVE"));
        interceptor.preSend(message(connect), null);
        return connect;
    }

    private StompHeaderAccessor subscribeAs(StompHeaderAccessor connect, String destination) {
        StompHeaderAccessor subscribe = accessor(StompCommand.SUBSCRIBE);
        subscribe.setDestination(destination);
        subscribe.setUser(connect.getUser());
        subscribe.setSessionAttributes(connect.getSessionAttributes());
        return subscribe;
    }

    private void assertSubscribeAllowed(StompHeaderAccessor connect, String destination) {
        assertThatCode(() -> interceptor.preSend(message(subscribeAs(connect, destination)), null))
                .as("subscribe to %s", destination)
                .doesNotThrowAnyException();
    }

    private void assertSubscribeDenied(StompHeaderAccessor connect, String destination) {
        assertThatThrownBy(() -> interceptor.preSend(message(subscribeAs(connect, destination)), null))
                .as("subscribe to %s", destination)
                .isInstanceOf(MessageDeliveryException.class);
    }

    // ---- CONNECT ----

    @Test
    void aValidTokenAttachesTheTenantScopedPrincipalAndRegistersTheTokenExpiry() {
        StompHeaderAccessor connect = connected();

        assertThat(connect.getUser()).isInstanceOf(RealtimePrincipal.class);
        RealtimePrincipal principal = (RealtimePrincipal) connect.getUser();
        assertThat(principal.getName()).isEqualTo(String.valueOf(USER_ID));
        assertThat(principal.companyId()).isEqualTo(COMPANY);
        assertThat(principal.hasBranch(BRANCH_MINE)).isTrue();
        assertThat(principal.hasBranch(BRANCH_OTHER)).isFalse();
        verify(sessionRegistry).authenticate(org.mockito.ArgumentMatchers.eq("session-1"), any(Instant.class));
    }

    @Test
    void connectWithoutAnAuthorizationHeaderIsRejected() {
        StompHeaderAccessor connect = accessor(StompCommand.CONNECT);

        assertThatThrownBy(() -> interceptor.preSend(message(connect), null))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(tokenProvider, userDetailsService);
    }

    @Test
    void connectWithANonBearerHeaderIsRejected() {
        StompHeaderAccessor connect = accessor(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Basic abc");

        assertThatThrownBy(() -> interceptor.preSend(message(connect), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connectWithAnInvalidOrExpiredTokenIsRejected() {
        StompHeaderAccessor connect = accessor(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer bad-token");
        when(tokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(connect), null))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(userDetailsService);
        assertThat(connect.getUser()).isNull();
    }

    @Test
    void connectForADeactivatedUserIsRejectedEvenWithAValidSignature() {
        StompHeaderAccessor connect = accessor(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer good-token");
        when(tokenProvider.validateToken("good-token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("good-token")).thenReturn(USER_ID);
        when(userDetailsService.loadUserById(USER_ID)).thenReturn(principal("INACTIVE"));

        assertThatThrownBy(() -> interceptor.preSend(message(connect), null))
                .isInstanceOf(MessageDeliveryException.class);
        verify(sessionRegistry, never()).authenticate(any(), any());
    }

    @Test
    void connectForAUserThatNoLongerExistsIsRejected() {
        StompHeaderAccessor connect = accessor(StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer good-token");
        when(tokenProvider.validateToken("good-token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("good-token")).thenReturn(USER_ID);
        when(userDetailsService.loadUserById(USER_ID)).thenThrow(new UsernameNotFoundException("gone"));

        assertThatThrownBy(() -> interceptor.preSend(message(connect), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    // ---- SUBSCRIBE ----

    @Test
    void subscribingToYourOwnCompanyTopicsIsAllowed() {
        StompHeaderAccessor connect = connected();

        assertSubscribeAllowed(connect, "/topic/company.4.stock");
        assertSubscribeAllowed(connect, "/topic/company.4.transfers");
    }

    @Test
    void subscribingToAnotherCompanysTopicsIsDenied() {
        StompHeaderAccessor connect = connected();

        assertSubscribeDenied(connect, "/topic/company.5.stock");
        assertSubscribeDenied(connect, "/topic/company.5.transfers");
    }

    @Test
    void aBranchTopicRequiresBothTheCompanyAndAnAssignedBranch() {
        StompHeaderAccessor connect = connected();

        assertSubscribeAllowed(connect, "/topic/company.4.branch.10.sales");
        assertSubscribeDenied(connect, "/topic/company.4.branch.99.sales"); // same company, unassigned branch
        assertSubscribeDenied(connect, "/topic/company.5.branch.10.sales"); // right branch id, wrong company
    }

    @Test
    void theUsersOwnNotificationQueueIsAllowed() {
        StompHeaderAccessor connect = connected();

        assertSubscribeAllowed(connect, "/user/queue/notifications");
    }

    @Test
    void unknownWildcardAndMalformedDestinationsAreDenied() {
        StompHeaderAccessor connect = connected();

        assertSubscribeDenied(connect, "/topic/**");
        assertSubscribeDenied(connect, "/topic/company.4.*");
        assertSubscribeDenied(connect, "/topic/company.4.stock/extra");
        assertSubscribeDenied(connect, "/topic/company.4.audit");
        assertSubscribeDenied(connect, "/topic/company..stock");
        assertSubscribeDenied(connect, "/queue/notifications");
        assertSubscribeDenied(connect, "/user/queue/other");
    }

    @Test
    void subscribingBeforeAuthenticatingIsDenied() {
        StompHeaderAccessor subscribe = accessor(StompCommand.SUBSCRIBE);
        subscribe.setDestination("/topic/company.4.stock");

        assertThatThrownBy(() -> interceptor.preSend(message(subscribe), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void aSocketCannotHoldAnUnboundedNumberOfSubscriptions() {
        StompHeaderAccessor connect = connected();

        for (int i = 0; i < 50; i++) {
            assertSubscribeAllowed(connect, "/topic/company.4.stock");
        }
        assertSubscribeDenied(connect, "/topic/company.4.stock");
    }

    // ---- SEND / DISCONNECT ----

    @Test
    void clientsCanNeverPublish() {
        StompHeaderAccessor connect = connected();
        StompHeaderAccessor send = accessor(StompCommand.SEND);
        send.setDestination("/topic/company.4.stock");
        send.setUser(connect.getUser());

        assertThatThrownBy(() -> interceptor.preSend(message(send), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void anUnauthenticatedClientCanStillDisconnect() {
        StompHeaderAccessor disconnect = accessor(StompCommand.DISCONNECT);

        assertThatCode(() -> interceptor.preSend(message(disconnect), null)).doesNotThrowAnyException();
    }
}
