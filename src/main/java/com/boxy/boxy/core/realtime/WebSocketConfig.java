package com.boxy.boxy.core.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.CloseStatus;

/**
 * STOMP over a plain WebSocket at {@code /ws} (no SockJS — every supported browser has native
 * WebSockets). The broker is Spring's in-memory simple broker, which is right for a single server
 * instance; running several would need {@code enableStompBrokerRelay} against RabbitMQ/Redis so an
 * event reaches sockets held by other instances.
 * <p>
 * The channel is server-to-client only: nothing is mapped under {@code /app}, and
 * {@link StompAuthChannelInterceptor} refuses client SEND frames outright.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_MS = 10_000;

    private final StompAuthChannelInterceptor authInterceptor;
    private final RealtimeSessionRegistry sessionRegistry;

    /** Same allow-list REST CORS uses — a WebSocket handshake isn't covered by CORS, so the origin
     *  is checked here instead (which is also what stops cross-site WebSocket hijacking). */
    @Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200,https://boxy-fe.vercel.app}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins.split(","));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(realtimeHeartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Slow-consumer protection: a client that stops reading can't make the server buffer
        // unboundedly on its behalf.
        registration.setMessageSizeLimit(64 * 1024)
                .setSendTimeLimit(10_000)
                .setSendBufferSizeLimit(512 * 1024);

        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessionRegistry.register(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                sessionRegistry.unregister(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }

    @Bean
    public ThreadPoolTaskScheduler realtimeHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
