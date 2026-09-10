package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.administration.dto.TaxDto;
import com.boxy.boxy.modules.administration.service.TaxService;
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
@RequestMapping({"/api/v1/administration/taxes", "/api/v1/administration/settings/taxes"})
@RequiredArgsConstructor
@Tag(name = "Administration - Taxes", description = "Endpoints for managing tax configurations")
public class TaxController {

    private final TaxService taxService;

    @GetMapping
    @PreAuthorize("hasAuthority('settings:view') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "List all company tax configurations")
    public ResponseEntity<ApiResponse<List<TaxDto>>> getAllTaxes() {
        return ResponseEntity.ok(ApiResponse.ok(taxService.getAllTaxes()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('settings:create') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Create a tax configuration")
    public ResponseEntity<ApiResponse<TaxDto>> createTax(@Valid @RequestBody TaxDto request) {
        TaxDto created = taxService.createTax(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Tax created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('settings:update') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update tax configuration")
    public ResponseEntity<ApiResponse<TaxDto>> updateTax(@PathVariable Long id, @Valid @RequestBody TaxDto request) {
        return ResponseEntity.ok(ApiResponse.ok(taxService.updateTax(id, request), "Tax updated successfully"));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('settings:update') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Toggle tax active status")
    public ResponseEntity<ApiResponse<TaxDto>> deactivateTax(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(taxService.deactivateTax(id)));
    }
}
