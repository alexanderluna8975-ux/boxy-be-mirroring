package com.boxy.boxy.modules.auth.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.auth.dto.LoginRequest;
import com.boxy.boxy.modules.auth.dto.LoginResponse;
import com.boxy.boxy.modules.auth.dto.RefreshTokenRequest;
import com.boxy.boxy.modules.auth.dto.UserProfileDto;
import com.boxy.boxy.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication, token refresh and profile retrieval")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT tokens")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using a valid refresh token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(response, "Token refreshed successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get currently authenticated user profile")
    public ResponseEntity<ApiResponse<UserProfileDto>> getCurrentUser() {
        String userId = SecurityUtils.getCurrentUserId();
        UserProfileDto profile = authService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }
}
