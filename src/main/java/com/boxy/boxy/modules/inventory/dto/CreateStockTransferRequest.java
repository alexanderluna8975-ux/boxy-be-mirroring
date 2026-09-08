package com.boxy.boxy.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateStockTransferRequest {
    @NotBlank(message = "Source warehouse is required")
    private Long sourceWarehouseId;

    @NotBlank(message = "Destination warehouse is required")
    private Long destinationWarehouseId;

    private String notes;

    @NotEmpty(message = "At least one item is required")
    private List<TransferItemRequest> items;

    @Data
    public static class TransferItemRequest {
        @NotBlank(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;
    }
}