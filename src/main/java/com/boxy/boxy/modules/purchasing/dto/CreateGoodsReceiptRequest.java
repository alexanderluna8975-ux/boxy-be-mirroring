package com.boxy.boxy.modules.purchasing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateGoodsReceiptRequest {
    @NotBlank(message = "Purchase Order ID is required")
    private String purchaseOrderId;

    @NotBlank(message = "Destination warehouse ID is required")
    private String warehouseId;

    private String supplierInvoiceNumber;
    private String notes;

    @NotEmpty(message = "At least one item is required")
    private List<GoodsReceiptItemRequest> items;

    @Data
    public static class GoodsReceiptItemRequest {
        @NotBlank(message = "Product ID is required")
        private String productId;

        @NotBlank(message = "Purchase order item ID is required")
        private String purchaseOrderItemId;

        private BigDecimal quantityReceived;
        private BigDecimal unitCost;
    }
}
