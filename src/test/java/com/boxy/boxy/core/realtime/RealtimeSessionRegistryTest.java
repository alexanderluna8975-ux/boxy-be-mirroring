package com.boxy.boxy.core.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeSessionRegistryTest {

    private final RealtimeSessionRegistry registry = new RealtimeSessionRegistry(15);

    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void closesASocketOnceItsTokenHasExpired() throws IOException {
        WebSocketSession session = openSession("s1");
        registry.register(session);
        registry.authenticate("s1", Instant.now().plusSeconds(60));

        assertThat(registry.sweep(Instant.now())).isZero();
        verify(session, never()).close(any(CloseStatus.class));

        assertThat(registry.sweep(Instant.now().plusSeconds(61))).isEqualTo(1);
        verify(session).close(any(CloseStatus.class));
        assertThat(registry.openSessionCount()).isZero();
    }

    @Test
    void closesASocketThatNeverAuthenticatedAfterTheGracePeriod() throws IOException {
        WebSocketSession session = openSession("s2");
        registry.register(session);

        assertThat(registry.sweep(Instant.now())).isZero(); // still inside the grace period
        assertThat(registry.sweep(Instant.now().plusSeconds(16))).isEqualTo(1);
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void leavesAnAuthenticatedSocketAloneEvenPastTheUnauthenticatedGracePeriod() throws IOException {
        WebSocketSession session = openSession("s3");
        registry.register(session);
        registry.authenticate("s3", Instant.now().plusSeconds(900));

        assertThat(registry.sweep(Instant.now().plusSeconds(120))).isZero();
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    void forgetsASocketTheClientClosedItself() {
        registry.register(openSession("s4"));

        registry.unregister("s4");

        assertThat(registry.openSessionCount()).isZero();
    }

    @Test
    void aFailureToCloseOneSocketDoesNotStopTheSweep() throws IOException {
        WebSocketSession broken = openSession("s5");
        org.mockito.Mockito.doThrow(new IOException("boom")).when(broken).close(any(CloseStatus.class));
        WebSocketSession healthy = openSession("s6");
        registry.register(broken);
        registry.register(healthy);
        registry.authenticate("s5", Instant.now().minusSeconds(1));
        registry.authenticate("s6", Instant.now().minusSeconds(1));

        assertThat(registry.sweep(Instant.now())).isEqualTo(2);
        verify(healthy).close(any(CloseStatus.class));
    }
}
