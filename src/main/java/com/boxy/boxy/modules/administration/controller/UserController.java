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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/administration/users")
@RequiredArgsConstructor
@Tag(name = "Administration - Users", description = "Endpoints for managing organization users")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('administration:manage') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "List all company users")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok(userService.getAllUsers()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('administration:manage') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Get user details by ID")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('administration:manage') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Create a new user")
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.createUser(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "User created successfully"), HttpStatus.CREATED);
    }
}
