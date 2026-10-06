package com.boxy.boxy.modules.auth.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.security.CustomUserDetailsService;
import com.boxy.boxy.core.security.JwtTokenProvider;
import com.boxy.boxy.core.security.RecaptchaService;
import com.boxy.boxy.core.security.UserPrincipal;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.auth.entity.RefreshToken;
import com.boxy.boxy.modules.auth.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression for the Fase 0/2 hardening: a refresh must be rejected for a deactivated user even
 * after {@code RefreshTokenService} itself accepts the raw token, and a bad password must count
 * toward — and eventually trigger — the account lockout.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private JwtTokenProvider tokenProvider;
    @Mock private RecaptchaService recaptchaService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private DeviceService deviceService;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private UserRepository userRepository;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ObjectMapper objectMapper;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private AuthService authService;

    private static UserPrincipal principal(String status) {
        return UserPrincipal.create(9L, 4L, "ana", "ana@boxy.dev", "hash", "Ana", 1L, status, List.of());
    }

    @Test
    void refreshPropagatesRejectionOfAnInvalidToken() {
        when(refreshTokenService.rotate(anyString(), any(), any(), any(), any()))
                .thenThrow(new BusinessException("INVALID_REFRESH_TOKEN", "invalid"));

        assertThatThrownBy(() -> authService.refresh("garbage", "127.0.0.1", "test-agent", null, null))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(userDetailsService, deviceService);
    }

    @Test
    void rejectsARefreshForADeactivatedUser() {
        User user = User.builder().id(9L).status("INACTIVE").build();
        RefreshTokenService.Issued issued = new RefreshTokenService.Issued("new-raw-token", new RefreshToken());
        when(refreshTokenService.rotate(anyString(), any(), any(), any(), any()))
                .thenReturn(new RefreshTokenService.Rotated(user, issued));

        assertThatThrownBy(() -> authService.refresh("refresh-token", "127.0.0.1", "test-agent", null, null))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(userDetailsService, deviceService);
    }

    @Test
    void rejectsLoginWhenRecaptchaFailsWithoutTouchingThePasswordOrTheLockoutCounter() {
        doThrow(new BusinessException("RECAPTCHA_FAILED", "reCAPTCHA verification failed."))
                .when(recaptchaService).verify(any(), anyString(), anyString());

        LoginRequest request = new LoginRequest();
        request.setUsername("ana");
        request.setPassword("whatever");
        request.setRecaptchaToken("bad-token");

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "test-agent", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "RECAPTCHA_FAILED");

        verifyNoInteractions(authenticationManager, userRepository, deviceService);
    }

    @Test
    void locksTheAccountAfterFiveConsecutiveBadPasswordsWithoutRegisteringADevice() {
        User user = User.builder().id(9L).username("ana").failedLoginCount(4).build();
        when(userRepository.findByUsernameOrEmail("ana")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad password"));

        LoginRequest request = new LoginRequest();
        request.setUsername("ana");
        request.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "test-agent", null, null))
                .isInstanceOf(BadCredentialsException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginCount()).isEqualTo(5);
        assertThat(captor.getValue().getLockedUntil()).isNotNull();
        verifyNoInteractions(deviceService);
    }

    @Test
    void aBadPasswordBelowTheThresholdDoesNotLockTheAccount() {
        User user = User.builder().id(9L).username("ana").failedLoginCount(0).build();
        when(userRepository.findByUsernameOrEmail("ana")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad password"));

        LoginRequest request = new LoginRequest();
        request.setUsername("ana");
        request.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "test-agent", null, null))
                .isInstanceOf(BadCredentialsException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginCount()).isEqualTo(1);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }
}
