package com.boxy.boxy.modules.dashboard.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.dashboard.dto.DashboardSummaryDto;
import com.boxy.boxy.modules.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard & Analytics", description = "Endpoints for KPI summary, analytics, and business insights")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "Get high-level dashboard business KPIs and summary")
    public ResponseEntity<ApiResponse<DashboardSummaryDto>> getSummary(@RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getSummary(branchId)));
    }
}
