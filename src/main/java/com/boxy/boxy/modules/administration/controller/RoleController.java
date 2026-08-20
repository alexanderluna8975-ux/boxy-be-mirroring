package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.administration.dto.PermissionDto;
import com.boxy.boxy.modules.administration.dto.RoleDto;
import com.boxy.boxy.modules.administration.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/administration")
@RequiredArgsConstructor
@Tag(name = "Administration - Roles & Permissions", description = "Endpoints for managing RBAC roles and permissions")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('administration:manage') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "List all company roles with their assigned permissions")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getAllRoles()));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('administration:manage') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "List all system permissions categorized by module")
    public ResponseEntity<ApiResponse<List<PermissionDto>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getAllPermissions()));
    }
}
