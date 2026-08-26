package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.administration.dto.PermissionDto;
import com.boxy.boxy.modules.administration.dto.RoleDto;
import com.boxy.boxy.modules.administration.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/administration")
@RequiredArgsConstructor
@Tag(name = "Administration - Roles & Permissions", description = "Endpoints for managing roles and system permissions")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/roles")
    @Operation(summary = "List all roles for the current company")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getAllRoles()));
    }

    @GetMapping("/roles/{id}")
    @Operation(summary = "Get role details by ID")
    public ResponseEntity<ApiResponse<RoleDto>> getRoleById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getRoleById(id)));
    }

    @GetMapping({"/permissions", "/permissions/matrix"})
    @Operation(summary = "List all available system permissions")
    public ResponseEntity<ApiResponse<List<PermissionDto>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getAllPermissions()));
    }
}