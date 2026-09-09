package com.boxy.boxy.modules.dashboard.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.dashboard.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports & Business Intelligence", description = "Endpoints for detailed reporting and analytics across sales, inventory, and finance")
public class ReportController {

    private final ReportService reportService;

    // --- SALES REPORTS ---

    @GetMapping("/sales/summary")
    @Operation(summary = "Sales summary KPI and trend report")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSalesSummary() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getSalesSummary()));
    }

    @GetMapping("/sales/by-product")
    @Operation(summary = "Sales breakdown by product")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSalesByProduct() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getSalesByProduct()));
    }

    @GetMapping("/sales/by-branch")
    @Operation(summary = "Sales breakdown by branch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSalesByBranch() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getSalesByBranch()));
    }

    @GetMapping("/sales/by-employee")
    @Operation(summary = "Sales breakdown by employee/cashier")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSalesByEmployee() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getSalesByEmployee()));
    }

    @GetMapping("/sales/quotation-funnel")
    @Operation(summary = "Quotation conversion funnel report")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQuotationFunnel() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getQuotationFunnel()));
    }

    // --- INVENTORY REPORTS ---

    @GetMapping("/inventory/summary")
    @Operation(summary = "Inventory valuation and stock health KPI summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInventorySummary() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getInventorySummary()));
    }

    @GetMapping("/inventory/critical-stock")
    @Operation(summary = "Products with critical or out-of-stock levels")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCriticalStock() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getCriticalStock()));
    }

    @GetMapping("/inventory/turnover")
    @Operation(summary = "Inventory turnover rates by SKU")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTurnover() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getTurnover()));
    }

    @GetMapping("/inventory/stock-movements")
    @Operation(summary = "Aggregated stock movements trend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStockMovements() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getStockMovementsReport()));
    }

    @GetMapping("/inventory/adjustment-activity")
    @Operation(summary = "Stock adjustment activity breakdown by reason")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdjustmentActivity() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getAdjustmentActivity()));
    }

    @GetMapping("/inventory/transfer-status")
    @Operation(summary = "Warehouse transfer status KPIs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTransferStatus() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getTransferStatus()));
    }

    @GetMapping("/inventory/stale")
    @Operation(summary = "Stale products with no recent movement")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStaleProducts() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getStaleProducts()));
    }

    // --- FINANCIAL REPORTS ---

    @GetMapping("/financial/summary")
    @Operation(summary = "Financial overview and gross profit KPIs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFinancialSummary() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getFinancialSummary()));
    }

    @GetMapping("/financial/revenue")
    @Operation(summary = "Revenue trends and category breakdown")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRevenue() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getRevenueReport()));
    }

    @GetMapping("/financial/taxes-discounts")
    @Operation(summary = "Taxes collected and discounts applied")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTaxesDiscounts() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getTaxesDiscounts()));
    }

    @GetMapping("/financial/payment-methods")
    @Operation(summary = "Revenue breakdown by payment method")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentMethods() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getPaymentMethods()));
    }

    @GetMapping("/financial/credit")
    @Operation(summary = "Customer credit exposure and balances")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCredit() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getCreditReport()));
    }

    @GetMapping("/financial/purchase-spend")
    @Operation(summary = "Purchase spend trend and supplier breakdown")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPurchaseSpend() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getPurchaseSpend()));
    }

    // --- ANALYTICS REPORTS ---

    @GetMapping("/analytics/trends")
    @Operation(summary = "Multi-metric business trends")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTrends() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getTrends()));
    }

    @GetMapping("/analytics/branch-comparison")
    @Operation(summary = "Comparative performance metrics across branches")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBranchComparison() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getBranchComparison()));
    }

    @GetMapping("/analytics/period-comparison")
    @Operation(summary = "Period-over-period comparative analysis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPeriodComparison() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getPeriodComparison()));
    }

    @GetMapping("/analytics/rankings")
    @Operation(summary = "Top rankings across products, categories, customers, and suppliers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRankings() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getRankings()));
    }

    @GetMapping("/branches")
    @Operation(summary = "List branches for reporting filter")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getReportBranches() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getReportBranches()));
    }
}
