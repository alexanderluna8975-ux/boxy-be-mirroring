package com.boxy.boxy.modules.auth.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.auth.entity.RefreshToken;
import com.boxy.boxy.modules.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The security-critical piece of Fase 2: a refresh token is single-use (rotation) and presenting
 * an already-used one is treated as theft (the whole family is killed, not just that one token).
 * Since device binding, a token presented from a different device (or a mismatched fingerprint,
 * when enforced) is treated the same way.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setExpiration() throws Exception {
        setField("refreshExpirationMs", 2_592_000_000L);
        setField("enforceFingerprint", true);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = RefreshTokenService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(refreshTokenService, value);
    }

    private static User user() {
        return User.builder().id(1L).username("ana").build();
    }

    @Test
    void issueSavesAHashNotTheRawTokenAndPersistsTheDeviceBinding() {
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            token.setId(100L);
            return token;
        });

        RefreshTokenService.Issued issued = refreshTokenService.issue(
                user(), "127.0.0.1", "test-agent", "device-hash-a", "fingerprint-hash-a");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(issued.rawToken());
        assertThat(captor.getValue().getTokenHash()).hasSize(64); // hex-encoded SHA-256
        assertThat(issued.rawToken()).isNotBlank();
        assertThat(captor.getValue().getDeviceIdHash()).isEqualTo("device-hash-a");
        assertThat(captor.getValue().getFingerprintHash()).isEqualTo("fingerprint-hash-a");
    }

    @Test
    void rotateRevokesTheOldTokenAndIssuesANewOneInTheSameFamilyInheritingItsDeviceBinding() {
        RefreshToken existing = RefreshToken.builder()
                .id(1L).user(user()).tokenHash("irrelevant-because-mocked-lookup")
                .familyId("family-a").expiresAt(Instant.now().plusSeconds(3600))
                .deviceIdHash("device-hash-a").fingerprintHash("fingerprint-hash-a")
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(
                "raw-token", "127.0.0.1", "test-agent", "device-hash-a", "fingerprint-hash-a");

        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(rotated.user().getId()).isEqualTo(1L);
        assertThat(rotated.issued().entity().getFamilyId()).isEqualTo("family-a");
        assertThat(rotated.issued().entity().getDeviceIdHash()).isEqualTo("device-hash-a");
        assertThat(rotated.issued().entity().getFingerprintHash()).isEqualTo("fingerprint-hash-a");
    }

    @Test
    void rotateRejectsAnExpiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .id(1L).user(user()).tokenHash("hash")
                .familyId("family-a").expiresAt(Instant.now().minusSeconds(1))
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token", "127.0.0.1", "test-agent", null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void presentingAnAlreadyRevokedTokenRevokesTheWholeFamily() {
        RefreshToken alreadyRevoked = RefreshToken.builder()
                .id(1L).user(user()).tokenHash("hash")
                .familyId("family-b").expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(Instant.now().minusSeconds(60))
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(alreadyRevoked));

        assertThatThrownBy(() -> refreshTokenService.rotate("stolen-raw-token", "127.0.0.1", "test-agent", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REFRESH_TOKEN_REUSED");

        verify(refreshTokenRepository).revokeFamily(eq("family-b"), any(Instant.class));
    }

    @Test
    void unknownTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("never-issued", "127.0.0.1", "test-agent", null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void aTokenWithNoDeviceBindingRotatesWithoutEnforcingOne() {
        // Simulates a token issued before device binding shipped (V26) — must not be locked out
        // of refreshing just because the deploy happened.
        RefreshToken legacy = RefreshToken.builder()
                .id(1L).user(user()).tokenHash("hash")
                .familyId("family-c").expiresAt(Instant.now().plusSeconds(3600))
                .deviceIdHash(null).fingerprintHash(null)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(legacy));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(
                "raw-token", "127.0.0.1", "test-agent", "some-other-device-hash", "some-fingerprint");

        assertThat(rotated.issued().entity().getDeviceIdHash()).isNull();
    }

    @Test
    void rotateRevokesTheFamilyWhenPresentedFromADifferentDeviceCookie() {
        RefreshToken existing = RefreshToken.builder()
                .id(1L).user(user()).tokenHash("hash")
                .familyId("family-d").expiresAt(Instant.now().plusSeconds(3600))
                .deviceIdHash("device-hash-original").fingerprintHash(null)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> refreshTokenService.rotate(
                "raw-token", "127.0.0.1", "test-agent", "device-hash-DIFFERENT", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");

        verify(refreshTokenRepository).revokeFamily(eq("family-d"), any(Instant.class));
    }

    @Test
    void rotateRevokesTheFamilyWhenTheFingerprintMismatchesAndEnforcementIsOn() {
        RefreshToken existing = RefreshToken.builder()
                .id(1L).user(user()).tokenHash("hash")
                .familyId("family-e").expiresAt(Instant.now().plusSeconds(3600))
                .deviceIdHash("device-hash-a").fingerprintHash("fingerprint-original")
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> refreshTokenService.rotate(
                "raw-token", "127.0.0.1", "test-agent", "device-hash-a", "fingerprint-DIFFERENT"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");

        verify(refreshTokenRepository).revokeFamily(eq("family-e"), any(Instant.class));
    }

    @Test
    void aFingerprintMismatchIsOnlyLoggedWhenEnforcementIsOff() throws Exception {
        setField("enforceFingerprint", false);
        RefreshToken existing = RefreshToken.builder()
                .id(1L).user(user()).tokenHash("hash")
                .familyId("family-f").expiresAt(Instant.now().plusSeconds(3600))
                .deviceIdHash("device-hash-a").fingerprintHash("fingerprint-original")
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(
                "raw-token", "127.0.0.1", "test-agent", "device-hash-a", "fingerprint-DIFFERENT");

        assertThat(rotated.user().getId()).isEqualTo(1L);
    }
}
