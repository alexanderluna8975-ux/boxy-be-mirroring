package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.administration.dto.CreateUserRequest;
import com.boxy.boxy.modules.administration.dto.UserDto;
import com.boxy.boxy.modules.administration.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    @Operation(summary = "List all company users")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit) {
        List<UserDto> users = userService.getAllUsers();
        com.boxy.boxy.core.response.PageMeta meta = com.boxy.boxy.core.response.PageMeta.of(page, limit, (long) users.size());
        return ResponseEntity.ok(ApiResponse.paged(users, meta));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user details by ID")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new user")
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.createUser(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "User created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user by ID")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateUser(id, request), "User updated successfully"));
    }

    @PatchMapping("/{id}/deactivate")
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
