package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.administration.dto.CreateRoleRequest;
import com.boxy.boxy.modules.administration.dto.PermissionDto;
import com.boxy.boxy.modules.administration.dto.PermissionMatrixDto;
import com.boxy.boxy.modules.administration.dto.RoleDto;
import com.boxy.boxy.modules.administration.dto.RolePermissionsDto;
import com.boxy.boxy.modules.administration.dto.UpdateRolePermissionsRequest;
import com.boxy.boxy.modules.administration.dto.UpdateRoleRequest;
import com.boxy.boxy.modules.administration.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/administration")
@RequiredArgsConstructor
@Tag(name = "Administration - Roles & Permissions", description = "Endpoints for managing roles and system permissions")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('roles:view') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "List all roles for the current company")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getAllRoles()));
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('roles:view') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get role details by ID")
    public ResponseEntity<ApiResponse<RoleDto>> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getRoleById(id)));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('roles:create') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Create a new role (starts with no permissions — grant them via the permission matrix)")
    public ResponseEntity<ApiResponse<RoleDto>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleDto created = roleService.createRole(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Role created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('roles:update') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Rename or update a role's description (system roles are immutable)")
    public ResponseEntity<ApiResponse<RoleDto>> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.updateRole(id, request), "Role updated successfully"));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('roles:delete') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Delete a role (rejected if it's a system role or still has members)")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Role deleted successfully"));
    }

    @GetMapping("/roles/{id}/members")
    @PreAuthorize("hasAuthority('roles:view') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "List users assigned to a specific role")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getRoleMembers(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getRoleMembers(id)));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('roles:view') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "List all available system permissions (flat catalog)")
    public ResponseEntity<ApiResponse<List<PermissionDto>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getAllPermissions()));
    }

    @GetMapping("/permissions/matrix")
    @PreAuthorize("hasAuthority('roles:view') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get the full permission catalog plus each role's current grants")
    public ResponseEntity<ApiResponse<PermissionMatrixDto>> getPermissionMatrix() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getPermissionMatrix()));
    }

    @PutMapping("/permissions/matrix/{roleId}")
    @PreAuthorize("hasAuthority('roles:update') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Replace a role's permission grants")
    public ResponseEntity<ApiResponse<RolePermissionsDto>> updateRolePermissions(
            @PathVariable Long roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.updateRolePermissions(roleId, request.getPermissions()), "Permissions updated successfully"));
    }
}
