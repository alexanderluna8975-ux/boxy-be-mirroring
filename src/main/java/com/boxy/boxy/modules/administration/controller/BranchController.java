package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.administration.dto.BranchDto;
import com.boxy.boxy.modules.administration.dto.CreateBranchRequest;
import com.boxy.boxy.modules.administration.service.BranchService;
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
@RequestMapping({"/api/v1/administration/branches", "/api/v1/administration/settings/branches"})
@RequiredArgsConstructor
@Tag(name = "Administration - Branches", description = "Endpoints for managing branches and physical locations")
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @Operation(summary = "List all company branches")
    public ResponseEntity<ApiResponse<List<BranchDto>>> getAllBranches() {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getAllBranches()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get branch details by ID")
    public ResponseEntity<ApiResponse<BranchDto>> getBranchById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getBranchById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('administration:settings:manage') or hasAuthority('administration:manage') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_OWNER') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Create a new branch")
    public ResponseEntity<ApiResponse<BranchDto>> createBranch(@Valid @RequestBody CreateBranchRequest request) {
        BranchDto created = branchService.createBranch(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Branch created successfully"), HttpStatus.CREATED);
    }
}