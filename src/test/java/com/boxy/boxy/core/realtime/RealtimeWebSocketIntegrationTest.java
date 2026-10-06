package com.boxy.boxy.core.realtime;

import com.boxy.boxy.core.realtime.events.StockChange;
import com.boxy.boxy.core.realtime.events.StockChangedEvent;
import com.boxy.boxy.core.security.CustomUserDetailsService;
import com.boxy.boxy.core.security.JwtTokenProvider;
import com.boxy.boxy.core.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Drives the real server over a real WebSocket with a real STOMP client: handshake, origin check,
 * CONNECT authentication, tenant-scoped SUBSCRIBE, delivery, token-expiry and anonymous-socket
 * cleanup. Needs the same local MySQL {@code BoxyApplicationTests} needs (the whole context boots);
 * the user itself is stubbed, so no data has to exist.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.jwt.secret=" + RealtimeWebSocketIntegrationTest.SECRET,
                // Short on purpose so the expiry test doesn't have to wait: an "app" token lives 2s.
                "app.jwt.expiration-ms=2000",
                "app.recaptcha.enabled=false",
                "app.realtime.unauthenticated-timeout-seconds=1",
                "app.realtime.sweep-interval-ms=300"
        })
class RealtimeWebSocketIntegrationTest {

    static final String SECRET = "test-only-jwt-signing-secret-do-not-use-in-any-real-environment-1234567890";
    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final long COMPANY = 4L;
    private static final long OTHER_COMPANY = 5L;
    private static final long BRANCH = 10L;

    @LocalServerPort private int port;
    @Autowired private JwtTokenProvider expiringTokenProvider;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private CustomUserDetailsService userDetailsService;

    /** Same secret, long lifetime — for scenarios that must outlast the 2s "app" tokens. */
    private final JwtTokenProvider longLivedTokens = new JwtTokenProvider(SECRET, 600_000);

    private WebSocketStompClient stompClient;
    private UserPrincipal user;

    @BeforeEach
    void setUp() {
        user = UserPrincipal.create(7L, COMPANY, "ana", "ana@boxy.dev", "hash", "Ana", BRANCH,
                "ACTIVE", null, List.of(), Set.of(BRANCH));
        when(userDetailsService.loadUserById(7L)).thenReturn(user);

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // Text frames (plain strings sent by the transport tests) and JSON (the real envelopes).
        stompClient.setMessageConverter(new CompositeMessageConverter(
                List.of(new StringMessageConverter(), new MappingJackson2MessageConverter())));
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    private String url() {
        return "ws://localhost:" + port + "/ws";
    }

    private static WebSocketHttpHeaders origin(String origin) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        return headers;
    }

    private static StompHeaders bearer(String token) {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return headers;
    }

    /** Records what happened on the socket so a test can assert on the outcome, not on timing. */
    private static final class Probe extends StompSessionHandlerAdapter {
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch failed = new CountDownLatch(1);
        volatile StompSession session;

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            this.session = session;
            connected.countDown();
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            failed.countDown(); // an ERROR frame
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            failed.countDown(); // connection dropped by the server
        }
    }

    private Probe connect(String token, String origin) {
        Probe probe = new Probe();
        stompClient.connectAsync(url(), origin(origin), bearer(token), probe);
        return probe;
    }

    private BlockingQueue<String> subscribe(StompSession session, String destination) {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((String) payload);
            }
        });
        return received;
    }

    private BlockingQueue<Map<String, Object>> subscribeJson(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        return received;
    }

    private static StockChangedEvent stockEvent(long productId) {
        return new StockChangedEvent(COMPANY, 7L, BRANCH,
                List.of(new StockChange(productId, BigDecimal.TEN, BigDecimal.valueOf(30), BigDecimal.valueOf(25))), 99L);
    }

    private void publishCommitted(StockChangedEvent event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    /** SUBSCRIBE is processed asynchronously, so publish until the first delivery instead of guessing a delay. */
    private String publishUntilReceived(String destination, String body, BlockingQueue<String> received)
            throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            messagingTemplate.convertAndSend(destination, body);
            String message = received.poll(200, TimeUnit.MILLISECONDS);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    private String token() {
        return longLivedTokens.generateToken(user);
    }

    // ---- authentication ----

    @Test
    void aClientWithAValidTokenConnectsAndReceivesItsOwnCompanysEvents() throws Exception {
        Probe probe = connect(token(), ALLOWED_ORIGIN);
        assertThat(probe.connected.await(5, TimeUnit.SECONDS)).as("connected").isTrue();

        BlockingQueue<String> received = subscribe(probe.session, "/topic/company." + COMPANY + ".stock");

        assertThat(publishUntilReceived("/topic/company." + COMPANY + ".stock", "stock-changed", received))
                .isEqualTo("stock-changed");
    }

    @Test
    void aClientWithoutATokenIsNotConnected() throws Exception {
        Probe probe = connect(null, ALLOWED_ORIGIN);

        assertThat(probe.connected.await(3, TimeUnit.SECONDS)).as("connected").isFalse();
    }

    @Test
    void aClientWithAGarbageTokenIsNotConnected() throws Exception {
        Probe probe = connect("not.a.jwt", ALLOWED_ORIGIN);

        assertThat(probe.connected.await(3, TimeUnit.SECONDS)).as("connected").isFalse();
    }

    @Test
    void aHandshakeFromAnUntrustedOriginIsRefused() throws Exception {
        Probe probe = connect(token(), "https://evil.example");

        assertThat(probe.connected.await(3, TimeUnit.SECONDS)).as("connected").isFalse();
    }

    // ---- tenant isolation ----

    @Test
    void subscribingToAnotherCompanysTopicIsRefusedAndNothingLeaksAcross() throws Exception {
        Probe probe = connect(token(), ALLOWED_ORIGIN);
        assertThat(probe.connected.await(5, TimeUnit.SECONDS)).isTrue();

        BlockingQueue<String> received = subscribe(probe.session, "/topic/company." + OTHER_COMPANY + ".stock");

        assertThat(probe.failed.await(5, TimeUnit.SECONDS)).as("server rejected the subscription").isTrue();
        messagingTemplate.convertAndSend("/topic/company." + OTHER_COMPANY + ".stock", "secret");
        assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    // ---- domain events reaching the wire ----

    @Test
    @SuppressWarnings("unchecked")
    void aCommittedStockEventArrivesAsAnEnvelopeWithAbsoluteTotals() throws Exception {
        Probe probe = connect(token(), ALLOWED_ORIGIN);
        assertThat(probe.connected.await(5, TimeUnit.SECONDS)).isTrue();
        BlockingQueue<Map<String, Object>> received = subscribeJson(probe.session, "/topic/company." + COMPANY + ".stock");

        Map<String, Object> message = null;
        for (int attempt = 0; attempt < 20 && message == null; attempt++) {
            publishCommitted(stockEvent(5L));
            message = received.poll(200, TimeUnit.MILLISECONDS);
        }

        assertThat(message).as("stock message").isNotNull();
        assertThat(message.get("type")).isEqualTo("STOCK_CHANGED");
        assertThat(message.get("occurredAt")).isInstanceOf(String.class);
        Map<String, Object> data = (Map<String, Object>) message.get("data");
        assertThat(((Number) data.get("warehouseId")).longValue()).isEqualTo(7L);
        Map<String, Object> item = ((List<Map<String, Object>>) data.get("items")).get(0);
        assertThat(((Number) item.get("productId")).longValue()).isEqualTo(5L);
        assertThat(((Number) item.get("warehouseAvailable")).doubleValue()).isEqualTo(25.0);
        // No actor, no user data anywhere in what a whole company can read.
        assertThat(message.toString()).doesNotContain("actor").doesNotContain("99");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEventFromARolledBackTransactionNeverReachesAnyone() throws Exception {
        Probe probe = connect(token(), ALLOWED_ORIGIN);
        assertThat(probe.connected.await(5, TimeUnit.SECONDS)).isTrue();
        BlockingQueue<Map<String, Object>> received = subscribeJson(probe.session, "/topic/company." + COMPANY + ".stock");

        // Prove the subscription is live first, so silence below can only mean "not sent".
        Map<String, Object> live = null;
        for (int attempt = 0; attempt < 20 && live == null; attempt++) {
            publishCommitted(stockEvent(1L));
            live = received.poll(200, TimeUnit.MILLISECONDS);
        }
        assertThat(live).as("subscription live").isNotNull();
        received.clear();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(stockEvent(2L));
            status.setRollbackOnly();
        });
        publishCommitted(stockEvent(3L));

        Map<String, Object> next = received.poll(3, TimeUnit.SECONDS);
        assertThat(next).isNotNull();
        Map<String, Object> item = ((List<Map<String, Object>>) ((Map<String, Object>) next.get("data")).get("items")).get(0);
        assertThat(((Number) item.get("productId")).longValue())
                .as("the first message after the rollback is the later committed one, not the rolled-back one")
                .isEqualTo(3L);
    }

    @Test
    void aUserQueueMessageReachesOnlyThatUsersSocket() throws Exception {
        Probe probe = connect(token(), ALLOWED_ORIGIN);
        assertThat(probe.connected.await(5, TimeUnit.SECONDS)).isTrue();
        BlockingQueue<Map<String, Object>> received = subscribeJson(probe.session, "/user/queue/notifications");

        // Addressed to a different user id: must not arrive, however long we wait.
        messagingTemplate.convertAndSendToUser("8", "/queue/notifications", Map.of("title", "not for ana"));
        assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();

        // Addressed by user id 7 — the principal's name — it does.
        Map<String, Object> mine = null;
        for (int attempt = 0; attempt < 20 && mine == null; attempt++) {
            messagingTemplate.convertAndSendToUser("7", "/queue/notifications", Map.of("title", "for ana"));
            mine = received.poll(200, TimeUnit.MILLISECONDS);
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("title")).isEqualTo("for ana");
    }

    // ---- lifecycle ----

    @Test
    void aSocketIsClosedOnceTheTokenThatOpenedItHasExpired() throws Exception {
        String shortLivedToken = expiringTokenProvider.generateToken(user); // lives 2s
        Probe probe = connect(shortLivedToken, ALLOWED_ORIGIN);
        assertThat(probe.connected.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(probe.failed.await(8, TimeUnit.SECONDS)).as("socket closed after token expiry").isTrue();
    }

    @Test
    void anAnonymousSocketThatNeverAuthenticatesIsDropped() throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        WebSocketSession raw = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closed.countDown();
            }
        }, origin(ALLOWED_ORIGIN), java.net.URI.create(url())).get(5, TimeUnit.SECONDS);

        assertThat(raw.isOpen()).isTrue();
        assertThat(closed.await(6, TimeUnit.SECONDS)).as("dropped by the server").isTrue();
    }
}
