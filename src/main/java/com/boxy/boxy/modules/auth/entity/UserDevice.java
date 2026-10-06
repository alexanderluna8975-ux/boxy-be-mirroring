package com.boxy.boxy.modules.auth.entity;

import com.boxy.boxy.modules.administration.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A device a user has logged in from before — identified by the (hashed) {@code boxy_did} cookie
 * the browser carries, not by the user. The same physical machine used by two different accounts
 * (a shared computer) is two separate rows, one per user, which is exactly what "new device for
 * this account" should mean.
 * <p>
 * Only {@link #deviceIdHash} (SHA-256 of the raw cookie value) and {@link #fingerprintHash}
 * (SHA-256 the client already computed) are stored — same reasoning as
 * {@code RefreshToken#tokenHash}: nothing here can be turned back into a usable cookie value.
 */
@Entity
@Table(name = "user_devices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id_hash", nullable = false, length = 64)
    private String deviceIdHash;

    @Column(name = "fingerprint_hash", length = 64)
    private String fingerprintHash;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "first_seen_at", nullable = false)
    @Builder.Default
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    @Builder.Default
    private Instant lastSeenAt = Instant.now();
}
