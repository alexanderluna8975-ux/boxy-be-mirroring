package com.boxy.boxy.modules.purchasing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreatePurchaseOrderRequest {
    private Long branchId = 1L;

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    private LocalDate issueDate = LocalDate.now();

    @JsonAlias({"expectedDeliveryDate", "expectedDate"})
    private LocalDate expectedDeliveryDate;

    private String notes;

    @JsonAlias({"lines", "items"})
    private List<PurchaseOrderItemRequest> items;

    @Data
    public static class PurchaseOrderItemRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;

        @JsonAlias({"unitCost", "unitPrice"})
        private BigDecimal unitCost;

        private BigDecimal taxRate = BigDecimal.ZERO;
    }
}
