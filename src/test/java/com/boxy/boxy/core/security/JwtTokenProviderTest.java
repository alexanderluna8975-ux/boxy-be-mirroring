package com.boxy.boxy.core.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String VALID_SECRET =
            "test-only-jwt-signing-secret-do-not-use-in-any-real-environment-1234567890";

    private static UserPrincipal principal() {
        return UserPrincipal.create(
                7L, 3L, "carlos", "carlos@boxy.dev", "hash", "Carlos", 2L, "ACTIVE", List.of("sales:create"));
    }

    @Test
    void constructorRejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider(null, 900_000L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructorRejectsSecretShorterThan64Bytes() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short", 900_000L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generatesAValidatableAccessToken() {
        JwtTokenProvider provider = new JwtTokenProvider(VALID_SECRET, 900_000L);
        String accessToken = provider.generateToken(principal());

        assertThat(provider.validateToken(accessToken)).isTrue();
        assertThat(provider.getUserIdFromToken(accessToken)).isEqualTo(7L);
    }

    @Test
    void garbageTokenIsRejected() {
        JwtTokenProvider provider = new JwtTokenProvider(VALID_SECRET, 900_000L);

        assertThat(provider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        JwtTokenProvider issuer = new JwtTokenProvider(VALID_SECRET, 900_000L);
        JwtTokenProvider verifier = new JwtTokenProvider(
                "a-completely-different-test-secret-that-is-also-at-least-64-bytes-long-000", 900_000L);

        String accessToken = issuer.generateToken(principal());

        assertThat(verifier.validateToken(accessToken)).isFalse();
    }
}
