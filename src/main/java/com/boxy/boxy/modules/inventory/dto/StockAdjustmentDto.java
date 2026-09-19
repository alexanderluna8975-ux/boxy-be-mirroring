package com.boxy.boxy.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentDto {
    private Long id;
    private String folio;
    private String adjustmentNumber;
    private Long warehouseId;
    private String warehouseName;
    private String warehouseCode;
    private String reasonId;
    private String reasonName;
    private String reason;
    private String notes;
    private String status;
    private String requestedBy;
    private Instant requestedAt;
    private String approvedBy;
    private Instant approvedAt;
    private int lineCount;
    private List<StockAdjustmentLineDto> lines;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockAdjustmentLineDto {
        private Long id;
        private Long productId;
        private String sku;
        private String productName;
        private BigDecimal quantityDelta;
        private BigDecimal previousQuantity;
        /** JSON key must stay `countedQuantity` — matches boxy-fe's `AdjustmentLine.countedQuantity`
         *  (adjustment.interface.ts). The request DTO already aliases both spellings on the way in
         *  ({@code CreateStockAdjustmentRequest}'s `@JsonAlias`); this response DTO used to serialize
         *  as `newQuantity`, which the FE's detail-page column never reads — it rendered "undefined". */
        private BigDecimal countedQuantity;
        private BigDecimal unitCost;
    }
}
