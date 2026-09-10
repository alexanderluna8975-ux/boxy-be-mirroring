package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.core.web.PageableFactory;
import com.boxy.boxy.modules.administration.dto.AuditLogDto;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/administration/audit-logs", "/api/v1/administration/settings/audit-logs"})
@RequiredArgsConstructor
@Tag(name = "Administration - Audit Logs", description = "Endpoints for viewing system audit trail")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('audit-logs:view') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "List system audit logs, paginated and filtered")
    public ResponseEntity<ApiResponse<List<AuditLogDto>>> getAuditLogs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "filter.entity") String entity,
            @RequestParam(required = false, name = "filter.dateFrom") String dateFrom,
            @RequestParam(required = false, name = "filter.dateTo") String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer pageSize) {
        Pageable pageable = PageableFactory.of(page, limit, pageSize);
        Page<AuditLogDto> result = auditLogService.getAuditLogs(search, entity, dateFrom, dateTo, pageable);
        return ResponseEntity.ok(ApiResponse.paged(result.getContent(), PageMeta.from(result)));
    }
}
