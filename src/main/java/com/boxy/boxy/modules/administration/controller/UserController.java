package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.core.web.PageableFactory;
import com.boxy.boxy.modules.administration.dto.CreateUserRequest;
import com.boxy.boxy.modules.administration.dto.ResetPasswordRequest;
import com.boxy.boxy.modules.administration.dto.UpdateUserRequest;
import com.boxy.boxy.modules.administration.dto.UserDto;
import com.boxy.boxy.modules.administration.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/administration/users")
@RequiredArgsConstructor
@Tag(name = "Administration - Users", description = "Endpoints for managing organization users")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('users:view') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "List company users, paginated and filtered")
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "filter.role") Long roleId,
            @RequestParam(required = false, name = "filter.status") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer pageSize) {
        Pageable pageable = PageableFactory.of(page, limit, pageSize);
        Page<UserDto> paged = userService.getUsers(search, status, roleId, pageable);
        PageMeta meta = PageMeta.from(paged);
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAuthority('users:view') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get user details by ID")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('users:create') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Create a new user")
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.createUser(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "User created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAuthority('users:update') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update user profile and branch/role assignments by ID")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateUser(id, request), "User updated successfully"));
    }

    @PatchMapping("/{id:\\d+}/password")
    @PreAuthorize("hasAuthority('users:update') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Reset a user's password (administrator action)")
    public ResponseEntity<ApiResponse<UserDto>> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.resetPassword(id, request.getNewPassword()), "Password updated successfully"));
    }

    @PatchMapping("/{id:\\d+}/deactivate")
    @PreAuthorize("hasAuthority('users:update') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Toggle user active status")
    public ResponseEntity<ApiResponse<UserDto>> deactivateUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.deactivateUser(id)));
    }

    @GetMapping("/check-unique")
    @Operation(summary = "Check whether an email/username is available")
    public ResponseEntity<Map<String, Boolean>> checkUserUnique(
            @RequestParam String field,
            @RequestParam String value,
            @RequestParam(required = false) Long excludeId) {
        boolean available = userService.checkUserUnique(field, value, excludeId);
        return ResponseEntity.ok(Collections.singletonMap("available", available));
    }
}
