package com.boxy.boxy.modules.auth.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.security.CustomUserDetailsService;
import com.boxy.boxy.core.security.Hashing;
import com.boxy.boxy.core.security.JwtTokenProvider;
import com.boxy.boxy.core.security.PasswordPolicy;
import com.boxy.boxy.core.security.RecaptchaService;
import com.boxy.boxy.core.security.UserPrincipal;
import com.boxy.boxy.modules.administration.dto.UserPermissionOverridesDto;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.auth.dto.BranchAssignmentDto;
import com.boxy.boxy.modules.auth.dto.ChangePasswordRequest;
import com.boxy.boxy.modules.auth.dto.LoginRequest;
import com.boxy.boxy.modules.auth.dto.LoginResponse;
import com.boxy.boxy.modules.auth.dto.NewDeviceAlertDto;
import com.boxy.boxy.modules.auth.dto.UserProfileDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** After this many consecutive bad-password attempts, the account locks — see
     *  {@link #registerLoginFailure}. Matches {@code UserPrincipal.isAccountNonLocked}. */
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final JwtTokenProvider tokenProvider;
    private final RecaptchaService recaptchaService;
    private final RefreshTokenService refreshTokenService;
    private final DeviceService deviceService;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Value("${app.jwt.expiration-ms:900000}")
    private long jwtExpirationMs;

    /** What a successful login/refresh produces: the JSON body (access token, no refresh token —
     *  that only ever travels as an httpOnly cookie, set by the caller in {@code AuthController})
     *  plus the raw refresh token value and its lifetime, for the controller to put in that cookie.
     *  {@code deviceCookie} is the raw {@code boxy_did} value to set, only on a login from a
     *  browser that didn't already have one — {@code null} otherwise (an existing device cookie
     *  is never rotated, and a refresh never mints one at all — see {@code DeviceService}). */
    public record AuthSession(LoginResponse body, String refreshToken, long refreshMaxAgeSeconds, String deviceCookie) {}

    @Transactional
    public AuthSession login(LoginRequest request, String ip, String userAgent, String deviceCookie, String fingerprint) {
        // Verified before anything else touches the password: a bot farming rejected captchas
        // must never count toward account lockout or cost a BCrypt comparison — see RecaptchaService.
        recaptchaService.verify(request.getRecaptchaToken(), ip, "login");

        UserPrincipal userPrincipal;
        try {
            // Delegates to the configured AuthenticationProvider (DaoAuthenticationProvider),
            // which checks the password AND UserDetails.isEnabled()/isAccountNonLocked()/etc. —
            // a deactivated or locked-out user is rejected here with Disabled/LockedException
            // instead of being able to log in like before.
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            userPrincipal = (UserPrincipal) authentication.getPrincipal();
        } catch (BadCredentialsException ex) {
            registerLoginFailure(request.getUsername());
            throw ex;
        }

        clearLoginFailures(userPrincipal.getId());
        auditLogService.recordForCompany(userPrincipal.getCompanyId(), userPrincipal.getId(),
                "Inicio de sesión exitoso", "Usuario", String.valueOf(userPrincipal.getId()),
                userPrincipal.getUsername(), null, null);
        // Device resolution happens only after a real, successful authentication — a bad-password
        // attempt (or a captcha rejection, above) must never register or fingerprint a device.
        return issueSession(userPrincipal, ip, userAgent, deviceCookie, fingerprint);
    }

    /** Only counts a genuine bad password (not a disabled/locked account, which shouldn't extend
     *  its own lockout just from being retried) — see the {@code catch} in {@link #login}. There's
     *  no company to attribute this to when the username/email doesn't match any account at all,
     *  so that case (the vast majority of credential-stuffing attempts) isn't logged — only a
     *  failure against a real, known account is. */
    private void registerLoginFailure(String usernameOrEmail) {
        userRepository.findByUsernameOrEmail(usernameOrEmail).ifPresent(user -> {
            int attempts = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(attempts);
            if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
            }
            userRepository.save(user);

            Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
            auditLogService.recordForCompany(companyId, user.getId(),
                    "Intento de inicio de sesión fallido", "Usuario", String.valueOf(user.getId()),
                    user.getUsername(), null, null);
        });
    }

    private void clearLoginFailures(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getFailedLoginCount() != 0 || user.getLockedUntil() != null) {
                user.setFailedLoginCount(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        });
    }

    /** Requires the presented raw refresh token itself to still be valid, active and unrevoked —
     *  {@link RefreshTokenService#rotate} enforces all of that (including reuse detection and,
     *  since device binding, that the request comes from the same device the token was issued
     *  to) before this ever gets a user back. A refresh token surviving past its owner's account
     *  being disabled/deleted is still rejected here, same as before. */
    @Transactional
    public AuthSession refresh(String rawRefreshToken, String ip, String userAgent, String deviceCookie, String fingerprint) {
        String presentedDeviceIdHash = deviceCookie != null && !deviceCookie.isBlank() ? Hashing.sha256Hex(deviceCookie) : null;
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(rawRefreshToken, ip, userAgent, presentedDeviceIdHash, fingerprint);
        User user = rotated.user();

        if (user.getDeletedAt() != null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "The provided refresh token is expired or invalid.");
        }

        UserPrincipal userPrincipal = (UserPrincipal) userDetailsService.loadUserById(user.getId());
        String newAccessToken = tokenProvider.generateToken(userPrincipal);

        // Never upserts a device here (unlike login) — a refresh from an unrecognized device
        // cookie was already rejected above by `rotate`'s binding check, so by this point the
        // cookie (if any) is either the one the token was issued to, or the token had no binding
        // at all yet. Either way there's nothing new to register, only alerts to surface.
        List<NewDeviceAlertDto> alerts = deviceService.pendingAlertsFor(user, presentedDeviceIdHash);

        LoginResponse body = LoginResponse.builder()
                .token(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .user(getProfile(user.getId()))
                .newDeviceAlerts(alerts)
                .build();

        return new AuthSession(body, rotated.issued().rawToken(), refreshTokenService.expirationMs() / 1000, null);
    }

    /** Revokes the whole refresh-token family the presented cookie belongs to — the controller
     *  clears the cookie itself regardless of whether this finds anything to revoke. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeFamilyOf(rawRefreshToken);
        }
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect.");
        }
        PasswordPolicy.rejectIfMatchesUsername(request.getNewPassword(), user.getUsername());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        // A changed password should end every other session immediately, not just stop new logins.
        refreshTokenService.revokeAllForUser(userId);
        auditLogService.record("Contraseña propia cambiada", "Usuario", String.valueOf(userId),
                user.getUsername(), null, null);
    }

    private AuthSession issueSession(UserPrincipal userPrincipal, String ip, String userAgent,
                                      String deviceCookie, String fingerprint) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userPrincipal.getId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        DeviceService.DeviceResolution device = deviceService.resolveAtLogin(user, deviceCookie, fingerprint, ip, userAgent);
        List<NewDeviceAlertDto> alerts = deviceService.pendingAlertsFor(user, device.deviceIdHash());

        String accessToken = tokenProvider.generateToken(userPrincipal);
        RefreshTokenService.Issued issued = refreshTokenService.issue(
                user, ip, userAgent, device.deviceIdHash(), device.fingerprintHash());

        UserProfileDto profile = getProfile(userPrincipal.getId());

        LoginResponse body = LoginResponse.builder()
                .token(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .user(profile)
                .newDeviceAlerts(alerts)
                .build();

        return new AuthSession(body, issued.rawToken(), refreshTokenService.expirationMs() / 1000, device.rawCookieToSet());
    }

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        List<BranchAssignmentDto> branches = user.getBranchRoles().stream()
                .map(ubr -> BranchAssignmentDto.builder()
                        .branchId(ubr.getBranch().getId())
                        .branchCode(ubr.getBranch().getCode())
                        .branchName(ubr.getBranch().getName())
                        .roleCode(ubr.getRole().getCode())
                        .roleName(ubr.getRole().getName())
                        .isDefault(Boolean.TRUE.equals(ubr.getIsDefault()))
                        .build())
                .toList();

        UserPermissionOverridesDto overrides = null;
        if (user.getPermissionOverrides() != null && !user.getPermissionOverrides().isBlank()) {
            try {
                overrides = objectMapper.readValue(user.getPermissionOverrides(), UserPermissionOverridesDto.class);
            } catch (Exception ignored) {
            }
        }

        boolean isSuperAdmin = user.getBranchRoles().stream()
                .anyMatch(ubr -> ubr.getRole() != null && "ROLE_SUPER_ADMIN".equalsIgnoreCase(ubr.getRole().getCode()));

        Set<String> effectivePermissions = user.getBranchRoles().stream()
                .filter(ubr -> ubr.getRole() != null)
                .flatMap(ubr -> ubr.getRole().getPermissions().stream())
                .map(com.boxy.boxy.modules.administration.entity.Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!isSuperAdmin && overrides != null) {
            if (overrides.getGranted() != null) {
                effectivePermissions.addAll(overrides.getGranted());
            }
            if (overrides.getRevoked() != null) {
                overrides.getRevoked().forEach(effectivePermissions::remove);
            }
        }

        Long activeBranchId = branches.stream()
                .filter(BranchAssignmentDto::isDefault)
                .map(BranchAssignmentDto::getBranchId)
                .findFirst()
                .orElse(branches.isEmpty() ? null : branches.get(0).getBranchId());

        return UserProfileDto.builder()
                .id(user.getId())
                .companyId(user.getCompany() != null ? user.getCompany().getId() : null)
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .activeBranchId(activeBranchId)
                .branches(branches)
                .permissions(new ArrayList<>(effectivePermissions))
                .permissionOverrides(overrides)
                .build();
    }
}