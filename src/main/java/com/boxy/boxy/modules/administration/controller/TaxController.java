package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.modules.administration.dto.AuditLogDto;
import com.boxy.boxy.modules.administration.dto.TaxDto;
import com.boxy.boxy.modules.administration.service.TaxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/administration")
@RequiredArgsConstructor
@Tag(name = "Administration - Settings", description = "Endpoints for taxes and audit logs")
public class TaxController {

    private final TaxService taxService;

    @GetMapping("/taxes")
    @Operation(summary = "List all company taxes")
    public ResponseEntity<ApiResponse<List<TaxDto>>> getAllTaxes() {
        return ResponseEntity.ok(ApiResponse.ok(taxService.getAllTaxes()));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('administration:manage') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Get paginated audit logs")
    public ResponseEntity<ApiResponse<List<AuditLogDto>>> getAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Page<AuditLogDto> paged = taxService.getAuditLogs(PageRequest.of(page - 1, limit));
        PageMeta meta = PageMeta.of(page, limit, paged.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }
}
