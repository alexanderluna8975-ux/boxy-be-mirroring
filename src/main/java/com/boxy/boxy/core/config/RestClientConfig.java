package com.boxy.boxy.core.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Applies a connect/read timeout to every autoconfigured {@code RestClient.Builder} in the app —
 * currently only consumed by {@link com.boxy.boxy.core.security.RecaptchaService}. Deliberately
 * done here, on the Spring-managed builder, rather than inside the service itself: a service that
 * calls {@code .requestFactory(...)} on a builder it was merely handed would clobber any request
 * factory the caller already configured on it — which breaks {@code MockRestServiceServer}-based
 * tests that bind a mock factory to a plain {@code RestClient.builder()} before constructing the
 * service. See {@code RecaptchaServiceTest}.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer recaptchaTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
            factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
            builder.requestFactory(factory);
        };
    }
}
