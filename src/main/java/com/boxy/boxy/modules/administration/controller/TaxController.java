package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.administration.dto.AuditLogDto;
import com.boxy.boxy.modules.administration.dto.TaxDto;
import com.boxy.boxy.modules.administration.service.TaxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/administration/taxes", "/api/v1/administration/settings/taxes"})
@RequiredArgsConstructor
@Tag(name = "Administration - Taxes", description = "Endpoints for managing tax configurations")
public class TaxController {

    private final TaxService taxService;

    @GetMapping
    @Operation(summary = "List all company tax configurations")
    public ResponseEntity<ApiResponse<List<TaxDto>>> getAllTaxes() {
        return ResponseEntity.ok(ApiResponse.ok(taxService.getAllTaxes()));
    }

    @PostMapping
    @Operation(summary = "Create a tax configuration")
    public ResponseEntity<ApiResponse<TaxDto>> createTax(@RequestBody TaxDto request) {
        TaxDto created = taxService.createTax(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Tax created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update tax configuration")
    public ResponseEntity<ApiResponse<TaxDto>> updateTax(@PathVariable Long id, @RequestBody TaxDto request) {
        return ResponseEntity.ok(ApiResponse.ok(taxService.updateTax(id, request), "Tax updated successfully"));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Toggle tax active status")
    public ResponseEntity<ApiResponse<TaxDto>> deactivateTax(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(taxService.deactivateTax(id)));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "List system audit logs with pagination")
    public ResponseEntity<ApiResponse<List<AuditLogDto>>> getAuditLogs(@PageableDefault(size = 20) Pageable pageable) {
        Page<AuditLogDto> page = taxService.getAuditLogs(pageable);
        return ResponseEntity.ok(ApiResponse.paged(page.getContent(), com.boxy.boxy.core.response.PageMeta.from(page)));
    }
}
