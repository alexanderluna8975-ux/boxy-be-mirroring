package com.boxy.boxy.modules.auth.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.security.Hashing;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.auth.entity.RefreshToken;
import com.boxy.boxy.modules.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Issues, rotates and revokes refresh tokens. The raw token is a 32-byte random value the caller
 * gets exactly once (as a Set-Cookie, in {@code AuthController}) — only its SHA-256 hash is ever
 * persisted, so a database read (backup, dump, careless log) can't be turned back into a usable
 * token. See {@link RefreshToken} for the rotation/reuse-detection design.
 * <p>
 * Since the device-fingerprint work, a token can also be bound to the device it was issued to
 * (see {@link RefreshToken#getDeviceIdHash()}) — {@link #rotate} enforces that binding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;

    @Value("${app.refresh-token.expiration-ms:2592000000}") // 30 days
    private long refreshExpirationMs;

    /** Toggle to fall back to "log only" if the fingerprint signal turns out too noisy in
     *  practice — the device-id cookie match is always enforced regardless of this flag. */
    @Value("${app.device.enforce-fingerprint:true}")
    private boolean enforceFingerprint;

    public long expirationMs() {
        return refreshExpirationMs;
    }

    public record Issued(String rawToken, RefreshToken entity) {}

    public record Rotated(User user, Issued issued) {}

    @Transactional
    public Issued issue(User user, String ip, String userAgent, String deviceIdHash, String fingerprintHash) {
        return issueInFamily(user, UUID.randomUUID().toString(), ip, userAgent, deviceIdHash, fingerprintHash);
    }

    /**
     * Verifies the presented raw token, then rotates it: the old row is revoked and a new one is
     * issued in the same family. Presenting a token that's already revoked is treated as reuse
     * (the value was copied and used from two places) — the entire family is revoked so every
     * token descended from it stops working, forcing a fresh login.
     * <p>
     * {@code presentedDeviceIdHash}/{@code presentedFingerprintHash} are what the caller sent on
     * this request (the {@code boxy_did} cookie's hash and the {@code X-Device-Fingerprint}
     * header) — checked against what was bound to the token at issuance. A token issued before
     * device binding shipped has a null {@link RefreshToken#getDeviceIdHash()} and is accepted
     * unconditionally; it becomes bound at the user's next login, not here.
     */
    @Transactional
    public Rotated rotate(String rawToken, String ip, String userAgent,
                           String presentedDeviceIdHash, String presentedFingerprintHash) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(Hashing.sha256Hex(rawToken))
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "The provided refresh token is expired or invalid."));

        if (existing.getRevokedAt() != null) {
            User compromisedUser = existing.getUser();
            log.warn("Refresh token reuse detected for family {} (user {}) — revoking the whole family.",
                    existing.getFamilyId(), compromisedUser.getId());
            refreshTokenRepository.revokeFamily(existing.getFamilyId(), Instant.now());
            Long companyId = compromisedUser.getCompany() != null ? compromisedUser.getCompany().getId() : null;
            auditLogService.recordForCompany(companyId, compromisedUser.getId(),
                    "Reutilización de refresh token detectada — sesión revocada", "Usuario",
                    String.valueOf(compromisedUser.getId()), compromisedUser.getUsername(), null, null);
            throw new BusinessException("REFRESH_TOKEN_REUSED", "The provided refresh token is expired or invalid.");
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "The provided refresh token is expired or invalid.");
        }

        User user = existing.getUser();

        if (existing.getDeviceIdHash() != null && isDeviceMismatch(existing, presentedDeviceIdHash, presentedFingerprintHash)) {
            log.warn("Refresh token presented from an unrecognized device for family {} (user {}) — revoking the whole family.",
                    existing.getFamilyId(), user.getId());
            refreshTokenRepository.revokeFamily(existing.getFamilyId(), Instant.now());
            Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
            auditLogService.recordForCompany(companyId, user.getId(),
                    "Sesión usada desde otro dispositivo — revocada", "Usuario",
                    String.valueOf(user.getId()), user.getUsername(), null, null);
            throw new BusinessException("INVALID_REFRESH_TOKEN", "The provided refresh token is expired or invalid.");
        }

        // Inherited from the predecessor, not re-taken from what was presented: the binding was
        // decided once, at login, and a rotation must never change it (nor silently drop it —
        // see the null-passthrough case above for a pre-device-binding token).
        Issued next = issueInFamily(user, existing.getFamilyId(), ip, userAgent,
                existing.getDeviceIdHash(), existing.getFingerprintHash());

        existing.setRevokedAt(Instant.now());
        existing.setReplacedById(next.entity().getId());
        refreshTokenRepository.save(existing);

        return new Rotated(user, next);
    }

    private boolean isDeviceMismatch(RefreshToken existing, String presentedDeviceIdHash, String presentedFingerprintHash) {
        boolean deviceIdMismatch = !existing.getDeviceIdHash().equals(presentedDeviceIdHash);
        boolean fingerprintMismatch = enforceFingerprint
                && existing.getFingerprintHash() != null
                && !existing.getFingerprintHash().equals(presentedFingerprintHash);
        return deviceIdMismatch || fingerprintMismatch;
    }

    /** Revokes the whole refresh-token family the presented raw token belongs to — used by logout. */
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        refreshTokenRepository.findByTokenHash(Hashing.sha256Hex(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    /** Revokes every refresh token a user holds, across every family/device — used when a
     *  password changes or resets, or the account is deactivated, so an existing session can't
     *  keep refreshing past that point. */
    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    private Issued issueInFamily(User user, String familyId, String ip, String userAgent,
                                  String deviceIdHash, String fingerprintHash) {
        String raw = Hashing.randomOpaqueToken();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(Hashing.sha256Hex(raw))
                .familyId(familyId)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .createdAt(Instant.now())
                .ip(ip)
                .userAgent(userAgent)
                .deviceIdHash(deviceIdHash)
                .fingerprintHash(fingerprintHash)
                .build();
        RefreshToken saved = refreshTokenRepository.save(token);
        return new Issued(raw, saved);
    }
}
