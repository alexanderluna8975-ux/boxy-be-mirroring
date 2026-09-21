package com.boxy.boxy.modules.auth.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.security.CustomUserDetailsService;
import com.boxy.boxy.core.security.JwtTokenProvider;
import com.boxy.boxy.core.security.UserPrincipal;
import com.boxy.boxy.modules.administration.dto.UserPermissionOverridesDto;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.auth.dto.BranchAssignmentDto;
import com.boxy.boxy.modules.auth.dto.ChangePasswordRequest;
import com.boxy.boxy.modules.auth.dto.LoginRequest;
import com.boxy.boxy.modules.auth.dto.LoginResponse;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Value("${app.jwt.expiration-ms:1800000}")
    private long jwtExpirationMs;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Delegates to the configured AuthenticationProvider (DaoAuthenticationProvider),
        // which checks the password AND UserDetails.isEnabled()/isAccountNonLocked()/etc. —
        // a deactivated user (status != ACTIVE) is rejected here with DisabledException
        // instead of being able to log in like before.
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        String token = tokenProvider.generateToken(userPrincipal);
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        UserProfileDto profile = getProfile(userPrincipal.getId());

        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .user(profile)
                .build();
    }

    @Transactional(readOnly = true)
    public LoginResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "The provided refresh token is expired or invalid.");
        }

        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        UserPrincipal userPrincipal = (UserPrincipal) userDetailsService.loadUserById(userId);

        String newToken = tokenProvider.generateToken(userPrincipal);
        String newRefreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        return LoginResponse.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .user(getProfile(userId))
                .build();
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
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