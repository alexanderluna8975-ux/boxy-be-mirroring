package com.boxy.boxy.modules.auth.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.security.CustomUserDetailsService;
import com.boxy.boxy.core.security.JwtTokenProvider;
import com.boxy.boxy.core.security.UserPrincipal;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.auth.dto.BranchAssignmentDto;
import com.boxy.boxy.modules.auth.dto.LoginRequest;
import com.boxy.boxy.modules.auth.dto.LoginResponse;
import com.boxy.boxy.modules.auth.dto.UserProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserPrincipal userPrincipal = (UserPrincipal) userDetailsService.loadUserByUsername(request.getUsername());

        log.info("Attempting login for user: {}, db password hash: {}", userPrincipal.getUsername(), userPrincipal.getPassword());
        boolean matches = passwordEncoder.matches(request.getPassword(), userPrincipal.getPassword());
        log.info("Password matches result: {}", matches);

        if (!matches) {
            String freshHash = passwordEncoder.encode(request.getPassword());
            log.info("Generated fresh BCrypt hash for '{}': {}", request.getPassword(), freshHash);
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid username or password.");
        }

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

        List<String> permissions = user.getBranchRoles().stream()
                .filter(ubr -> ubr.getRole() != null)
                .flatMap(ubr -> ubr.getRole().getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .toList();

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
                .permissions(permissions)
                .build();
    }
}