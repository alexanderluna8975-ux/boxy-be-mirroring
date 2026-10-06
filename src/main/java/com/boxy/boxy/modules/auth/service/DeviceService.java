package com.boxy.boxy.modules.auth.service;

import com.boxy.boxy.core.security.Hashing;
import com.boxy.boxy.core.security.UserAgentSummary;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.auth.dto.NewDeviceAlertDto;
import com.boxy.boxy.modules.auth.entity.UserDevice;
import com.boxy.boxy.modules.auth.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Recognizes the browser/device a login comes from, via the long-lived {@code boxy_did} httpOnly
 * cookie {@code AuthController} sets — separate from, and much longer-lived than, the refresh
 * token cookie itself. Two purposes:
 * <ol>
 *   <li>lets {@link RefreshTokenService#rotate} confirm a refresh is coming from the same device
 *   it was issued to (the binding itself is stored on the refresh token, not here);</li>
 *   <li>lets the account holder see "this account was used from a device you haven't seen
 *   before" the next time they open Boxy from a device that already knows them —
 *   {@link #pendingAlertsFor}.</li>
 * </ol>
 * Only the SHA-256 of the cookie value is ever persisted — see {@link Hashing}. Deliberately
 * keyed by {@code (user, deviceIdHash)}, not by device alone: the same physical browser used by
 * two different accounts (a shared/kiosk machine) is two separate rows, one per account, which is
 * exactly what "a new device for this account" should mean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    /** Client-computed fingerprints are always a 64-char hex SHA-256 — anything else is either a
     *  bug on the client or a hostile value, and gets dropped rather than stored or compared. */
    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    private final UserDeviceRepository userDeviceRepository;
    private final AuditLogService auditLogService;

    public record DeviceResolution(String rawCookieToSet, String deviceIdHash, String fingerprintHash) {}

    /**
     * Upserts the device this login came from. Generates and returns a new raw cookie value only
     * when {@code rawDeviceCookie} is missing (first time this browser is seen at all) — an
     * existing cookie is always kept as-is, never rotated, so a device stays recognizable across
     * logins. A first-time device is recorded in the audit log; a returning one just has its
     * ip/user-agent/fingerprint refreshed.
     * <p>
     * Deliberately does NOT touch {@code lastSeenAt} — that's {@link #pendingAlertsFor}'s job, so
     * it can still tell "since when" after this call resolves which device row we're talking
     * about. Call both, in order, from the login flow.
     */
    @Transactional
    public DeviceResolution resolveAtLogin(User user, String rawDeviceCookie, String fingerprint,
                                            String ip, String userAgent) {
        boolean isNewCookie = rawDeviceCookie == null || rawDeviceCookie.isBlank();
        String raw = isNewCookie ? Hashing.randomOpaqueToken() : rawDeviceCookie;
        String deviceIdHash = Hashing.sha256Hex(raw);
        String fingerprintHash = validFingerprintOrNull(fingerprint);

        userDeviceRepository.findByUserIdAndDeviceIdHash(user.getId(), deviceIdHash).ifPresentOrElse(
                existing -> {
                    existing.setIp(ip);
                    existing.setUserAgent(userAgent);
                    if (fingerprintHash != null) {
                        existing.setFingerprintHash(fingerprintHash);
                    }
                    userDeviceRepository.save(existing);
                },
                () -> {
                    Instant now = Instant.now();
                    UserDevice device = UserDevice.builder()
                            .user(user)
                            .deviceIdHash(deviceIdHash)
                            .fingerprintHash(fingerprintHash)
                            .ip(ip)
                            .userAgent(userAgent)
                            .firstSeenAt(now)
                            .lastSeenAt(now)
                            .build();
                    userDeviceRepository.save(device);

                    Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
                    auditLogService.recordForCompany(companyId, user.getId(),
                            "Inicio de sesión desde un dispositivo nuevo", "Usuario",
                            String.valueOf(user.getId()), user.getUsername(),
                            null, UserAgentSummary.summarize(userAgent) + " — IP " + ip);
                });

        return new DeviceResolution(isNewCookie ? raw : null, deviceIdHash, fingerprintHash);
    }

    /**
     * Every device of this user first seen since this one last checked in, excluding this device
     * itself — then marks this device as checked in "now", so the same alert is never shown
     * twice. Safe to call for a device cookie that doesn't match any known row (no cookie at all,
     * or one from before this feature shipped): returns an empty list rather than failing.
     */
    @Transactional
    public List<NewDeviceAlertDto> pendingAlertsFor(User user, String deviceIdHash) {
        if (deviceIdHash == null) {
            return List.of();
        }

        return userDeviceRepository.findByUserIdAndDeviceIdHash(user.getId(), deviceIdHash)
                .map(device -> {
                    List<UserDevice> newer = userDeviceRepository.findNewSince(
                            user.getId(), device.getLastSeenAt(), device.getId());

                    device.setLastSeenAt(Instant.now());
                    userDeviceRepository.save(device);

                    return newer.stream()
                            .map(d -> NewDeviceAlertDto.builder()
                                    .device(UserAgentSummary.summarize(d.getUserAgent()))
                                    .ip(d.getIp())
                                    .firstSeenAt(DateTimeFormatter.ISO_INSTANT.format(d.getFirstSeenAt()))
                                    .build())
                            .toList();
                })
                .orElse(List.of());
    }

    private static String validFingerprintOrNull(String fingerprint) {
        return fingerprint != null && HEX_64.matcher(fingerprint).matches() ? fingerprint : null;
    }
}
