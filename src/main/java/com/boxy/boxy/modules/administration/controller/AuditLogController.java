package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.modules.administration.dto.AuditLogDto;
import com.boxy.boxy.modules.administration.service.TaxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/administration/audit-logs", "/api/v1/administration/settings/audit-logs"})
@RequiredArgsConstructor
@Tag(name = "Administration - Audit Logs", description = "Endpoints for viewing system audit trail")
public class AuditLogController {

    private final TaxService taxService;

    @GetMapping
    @Operation(summary = "List system audit logs with pagination")
    public ResponseEntity<ApiResponse<List<AuditLogDto>>> getAuditLogs(@PageableDefault(size = 20) Pageable pageable) {
        Page<AuditLogDto> page = taxService.getAuditLogs(pageable);
        return ResponseEntity.ok(ApiResponse.paged(page.getContent(), PageMeta.from(page)));
    }
}
