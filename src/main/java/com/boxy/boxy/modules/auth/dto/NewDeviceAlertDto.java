package com.boxy.boxy.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One "this account was used from a device you haven't seen before" alert, surfaced once to a
 *  known device on its next login/refresh — see {@code DeviceService#pendingAlertsFor}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewDeviceAlertDto {
    /** Human label, e.g. "Chrome en Windows" — see {@code UserAgentSummary}. */
    private String device;
    private String ip;
    private String firstSeenAt;
}
