package com.boxy.boxy.modules.auth.controller;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.auth.dto.ChangePasswordRequest;
import com.boxy.boxy.modules.auth.dto.LoginRequest;
import com.boxy.boxy.modules.auth.dto.LoginResponse;
import com.boxy.boxy.modules.auth.dto.UserProfileDto;
import com.boxy.boxy.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication, token refresh and profile retrieval")
public class AuthController {

    /** Path-scoped to `/api/v1/auth` (not the whole API) — the browser only ever needs to send
     *  this cookie back to `/refresh` and `/logout`, never on every other request. */
    private static final String REFRESH_COOKIE_NAME = "boxy_rt";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    /** Long-lived device identifier — see {@code DeviceService}. Same path scope as
     *  {@link #REFRESH_COOKIE_NAME}: it only needs to travel to `/auth/*`. */
    private static final String DEVICE_COOKIE_NAME = "boxy_did";
    private static final String DEVICE_FINGERPRINT_HEADER = "X-Device-Fingerprint";

    private final AuthService authService;

    @Value("${app.auth-cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.device.cookie-max-age-days:400}")
    private int deviceCookieMaxAgeDays;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue an access token (refresh token is set as an httpOnly cookie)")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            @CookieValue(name = DEVICE_COOKIE_NAME, required = false) String deviceCookie,
            @RequestHeader(value = DEVICE_FINGERPRINT_HEADER, required = false) String fingerprint,
            HttpServletRequest httpRequest) {
        AuthService.AuthSession session = authService.login(
                request, httpRequest.getRemoteAddr(), userAgentOf(httpRequest), deviceCookie, fingerprint);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), session.refreshMaxAgeSeconds()).toString());
        if (session.deviceCookie() != null) {
            response.header(HttpHeaders.SET_COOKIE, deviceCookie(session.deviceCookie()).toString());
        }
        return response.body(ApiResponse.ok(session.body(), "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh-token cookie and issue a new access token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            @CookieValue(name = DEVICE_COOKIE_NAME, required = false) String deviceCookie,
            @RequestHeader(value = DEVICE_FINGERPRINT_HEADER, required = false) String fingerprint,
            // A simple, effective CSRF check for a cookie-authenticated endpoint: a cross-site
            // <form>/navigation can attach the cookie automatically, but can't set a custom
            // header, so requiring one here means only same-origin script (fetch/XHR) can call
            // this successfully — no CSRF token machinery needed on top of that.
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            HttpServletRequest httpRequest) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "The provided refresh token is expired or invalid.");
        }
        if (requestedWith == null || requestedWith.isBlank()) {
            throw new BusinessException("MISSING_REQUESTED_WITH", "This endpoint requires the X-Requested-With header.");
        }

        AuthService.AuthSession session = authService.refresh(
                refreshCookie, httpRequest.getRemoteAddr(), userAgentOf(httpRequest), deviceCookie, fingerprint);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), session.refreshMaxAgeSeconds()).toString())
                .body(ApiResponse.ok(session.body(), "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh-token family and clear its cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshCookie) {
        authService.logout(refreshCookie);
        // The device cookie is deliberately left alone: this device stays "known" to the account
        // even after logout, so signing back in from it won't fire a new-device alert.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .body(ApiResponse.ok(null, "Logged out successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get currently authenticated user profile")
    public ResponseEntity<ApiResponse<UserProfileDto>> getCurrentUser() {
        Long userId = SecurityUtils.requireCurrentUserId();
        UserProfileDto profile = authService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the current user's own password (requires the current password)")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.requireCurrentUserId();
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password updated successfully"));
    }

    private ResponseCookie refreshCookie(String rawToken, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie deviceCookie(String rawDeviceId) {
        return ResponseCookie.from(DEVICE_COOKIE_NAME, rawDeviceId)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofDays(deviceCookieMaxAgeDays))
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    private String userAgentOf(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        return userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
