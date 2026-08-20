package com.boxy.boxy.modules.purchasing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreatePurchaseOrderRequest {
    @NotBlank(message = "Branch ID is required")
    private String branchId;

    @NotBlank(message = "Supplier ID is required")
    private String supplierId;

    @NotNull(message = "Issue date is required")
    private LocalDate issueDate;

    private LocalDate expectedDeliveryDate;
    private String notes;

    @NotEmpty(message = "At least one item is required")
    private List<PurchaseOrderItemRequest> items;

    @Data
    public static class PurchaseOrderItemRequest {
        @NotBlank(message = "Product ID is required")
        private String productId;

        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;

        @NotNull(message = "Unit cost is required")
        private BigDecimal unitCost;

        private BigDecimal taxRate = BigDecimal.ZERO;
    }
}
