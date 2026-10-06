package com.boxy.boxy.modules.auth.entity;

import com.boxy.boxy.modules.administration.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A single refresh token in a rotation chain ("family"). Only {@link #tokenHash} (SHA-256 of the
 * opaque raw token the client holds) is ever stored — the raw value exists only in the Set-Cookie
 * response and the client's cookie jar, never in the database or logs.
 * <p>
 * Rotation: each successful {@code /auth/refresh} revokes the presented token
 * ({@link #revokedAt} + {@link #replacedById}) and issues a new one in the same
 * {@link #familyId}. If a token that's already revoked is presented again, that's a reuse
 * signal (the cookie was copied/stolen and used from two places) — see
 * {@code RefreshTokenService#rotate}, which revokes the whole family on that signal.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** SHA-256 of the {@code boxy_did} device-id cookie presented when this token was issued —
     *  null for tokens issued before device binding shipped (see V26 migration) and for the
     *  handful of legacy tokens that never get bound. {@code RefreshTokenService#rotate} only
     *  enforces the match when this is non-null. */
    @Column(name = "device_id_hash", length = 64)
    private String deviceIdHash;

    /** SHA-256 the client computed from stable browser signals, presented via
     *  {@code X-Device-Fingerprint} — a softer signal than {@link #deviceIdHash}, since it's
     *  reproducible by an attacker who controls the browser. Enforced only when
     *  {@code app.device.enforce-fingerprint=true}. */
    @Column(name = "fingerprint_hash", length = 64)
    private String fingerprintHash;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
