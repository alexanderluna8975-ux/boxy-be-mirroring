package com.boxy.boxy.modules.auth.service;

import com.boxy.boxy.core.security.Hashing;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.auth.dto.NewDeviceAlertDto;
import com.boxy.boxy.modules.auth.entity.UserDevice;
import com.boxy.boxy.modules.auth.repository.UserDeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock private UserDeviceRepository userDeviceRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private DeviceService deviceService;

    private static User user(long id) {
        return User.builder().id(id).username("ana").build();
    }

    @Test
    void withNoCookieCreatesADeviceGeneratesANewCookieAndRecordsAuditLog() {
        when(userDeviceRepository.findByUserIdAndDeviceIdHash(eq(1L), anyString())).thenReturn(Optional.empty());

        DeviceService.DeviceResolution resolution = deviceService.resolveAtLogin(
                user(1L), null, null, "127.0.0.1", "Mozilla/5.0 Chrome/120 Safari/537");

        assertThat(resolution.rawCookieToSet()).isNotBlank();
        assertThat(resolution.deviceIdHash()).isEqualTo(Hashing.sha256Hex(resolution.rawCookieToSet()));

        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(captor.capture());
        assertThat(captor.getValue().getDeviceIdHash()).isEqualTo(resolution.deviceIdHash());

        verify(auditLogService).recordForCompany(
                any(), eq(1L), eq("Inicio de sesión desde un dispositivo nuevo"),
                eq("Usuario"), eq("1"), anyString(), any(), anyString());
    }

    @Test
    void withAKnownCookieDoesNotCreateAnythingAndKeepsTheSameCookie() {
        String rawCookie = "existing-raw-device-id";
        String hash = Hashing.sha256Hex(rawCookie);
        UserDevice existing = UserDevice.builder()
                .id(50L).user(user(1L)).deviceIdHash(hash)
                .firstSeenAt(Instant.now().minusSeconds(3600)).lastSeenAt(Instant.now().minusSeconds(60))
                .build();
        when(userDeviceRepository.findByUserIdAndDeviceIdHash(1L, hash)).thenReturn(Optional.of(existing));

        DeviceService.DeviceResolution resolution = deviceService.resolveAtLogin(
                user(1L), rawCookie, null, "127.0.0.1", "test-agent");

        assertThat(resolution.rawCookieToSet()).isNull(); // no new cookie — the existing one is reused
        assertThat(resolution.deviceIdHash()).isEqualTo(hash);
        verify(userDeviceRepository, never()).save(argThat(d -> d.getId() == null));
        verify(auditLogService, never()).recordForCompany(any(), any(), eq("Inicio de sesión desde un dispositivo nuevo"),
                any(), any(), any(), any(), any());
    }

    @Test
    void theSameCookieForADifferentUserCountsAsANewDeviceForThatUser() {
        String rawCookie = "shared-machine-cookie";
        String hash = Hashing.sha256Hex(rawCookie);
        // user 1 already knows this cookie, but user 2 (logging in from the same shared machine)
        // does not — the lookup is scoped to (userId, hash), so it's a fresh row for user 2.
        when(userDeviceRepository.findByUserIdAndDeviceIdHash(2L, hash)).thenReturn(Optional.empty());

        DeviceService.DeviceResolution resolution = deviceService.resolveAtLogin(
                user(2L), rawCookie, null, "127.0.0.1", "test-agent");

        assertThat(resolution.rawCookieToSet()).isNull(); // the cookie itself isn't rotated
        verify(userDeviceRepository).save(any());
        verify(auditLogService).recordForCompany(any(), eq(2L), eq("Inicio de sesión desde un dispositivo nuevo"),
                any(), any(), any(), any(), any());
    }

    @Test
    void alertsAreReturnedOnlyOnceThenConsumed() {
        UserDevice thisDevice = UserDevice.builder()
                .id(10L).user(user(1L)).deviceIdHash("hash-this")
                .firstSeenAt(Instant.now().minusSeconds(7200)).lastSeenAt(Instant.now().minusSeconds(3600))
                .build();
        UserDevice otherNewDevice = UserDevice.builder()
                .id(11L).user(user(1L)).deviceIdHash("hash-other")
                .userAgent("Mozilla/5.0 Chrome/120").ip("9.9.9.9")
                .firstSeenAt(Instant.now().minusSeconds(1800)).lastSeenAt(Instant.now().minusSeconds(1800))
                .build();

        when(userDeviceRepository.findByUserIdAndDeviceIdHash(1L, "hash-this")).thenReturn(Optional.of(thisDevice));
        when(userDeviceRepository.findNewSince(eq(1L), any(Instant.class), eq(10L)))
                .thenReturn(List.of(otherNewDevice));

        List<NewDeviceAlertDto> alerts = deviceService.pendingAlertsFor(user(1L), "hash-this");

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getIp()).isEqualTo("9.9.9.9");
        assertThat(alerts.get(0).getDevice()).contains("Chrome");

        // The device's checkpoint is advanced — a second call in the same "session" would find
        // nothing new, since findNewSince is queried with whatever lastSeenAt was saved here. We
        // can't re-run the (mocked) query to prove that end-to-end, so we assert the save instead.
        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLastSeenAt()).isAfter(thisDevice.getFirstSeenAt());
    }

    @Test
    void pendingAlertsForAnUnrecognizedDeviceHashReturnsEmptyInsteadOfFailing() {
        when(userDeviceRepository.findByUserIdAndDeviceIdHash(1L, "unknown-hash")).thenReturn(Optional.empty());

        List<NewDeviceAlertDto> alerts = deviceService.pendingAlertsFor(user(1L), "unknown-hash");

        assertThat(alerts).isEmpty();
    }

    @Test
    void pendingAlertsForANullDeviceHashReturnsEmptyWithoutQuerying() {
        List<NewDeviceAlertDto> alerts = deviceService.pendingAlertsFor(user(1L), null);

        assertThat(alerts).isEmpty();
        verify(userDeviceRepository, never()).findByUserIdAndDeviceIdHash(anyLong(), anyString());
    }
}
