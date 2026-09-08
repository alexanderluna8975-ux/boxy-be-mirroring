package com.boxy.boxy.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {
    private BigDecimal totalSalesToday;
    private long totalOrdersToday;
    private long totalCustomers;
    private long totalProducts;
    private long lowStockAlertsCount;
    private List<RecentSaleDto> recentSales;
    private List<LowStockAlertDto> lowStockProducts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentSaleDto {
        private Long invoiceId;
        private String invoiceNumber;
        private String customerName;
        private BigDecimal totalAmount;
        private String createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LowStockAlertDto {
        private Long productId;
        private String productSku;
        private String productName;
        private BigDecimal availableStock;
        private BigDecimal minStockAlert;
    }
}
