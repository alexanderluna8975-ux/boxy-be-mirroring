package com.boxy.boxy.modules.purchasing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateGoodsReceiptRequest {
    @NotNull(message = "Purchase Order ID is required")
    private Long purchaseOrderId;

    private Long warehouseId;
    private String supplierInvoiceNumber;
    private String notes;

    @JsonAlias({"lines", "items"})
    private List<GoodsReceiptItemRequest> items;

    @Data
    public static class GoodsReceiptItemRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        private Long purchaseOrderItemId;
        private BigDecimal quantityReceived;
        private BigDecimal unitCost;
    }
}
