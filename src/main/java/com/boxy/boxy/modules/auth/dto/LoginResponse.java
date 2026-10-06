package com.boxy.boxy.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The refresh token is never in this body — it only ever travels as the {@code boxy_rt}
 *  httpOnly cookie {@code AuthController} sets alongside this response. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;
    private long expiresIn;
    private UserProfileDto user;
    /** Empty when there's nothing to report — see {@code DeviceService#pendingAlertsFor}. */
    @Builder.Default
    private List<NewDeviceAlertDto> newDeviceAlerts = List.of();
}
