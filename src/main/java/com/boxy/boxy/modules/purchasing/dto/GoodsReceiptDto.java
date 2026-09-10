package com.boxy.boxy.modules.purchasing.dto;

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
public class GoodsReceiptDto {
    private Long id;
    private String folio;
    private Long purchaseOrderId;
    private String purchaseOrderFolio;
    private Long supplierId;
    private String supplierName;
    private Long warehouseId;
    private String warehouseName;
    private int lineCount;
    private String notes;
    private String receivedBy;
    private Instant receivedAt;
    private List<GoodsReceiptLineDto> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoodsReceiptLineDto {
        private Long productId;
        private String sku;
        private String productName;
        private BigDecimal quantityReceived;
    }
}
