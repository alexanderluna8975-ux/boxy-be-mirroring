package com.boxy.boxy.modules.inventory.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateStockTransferRequest {
    @NotNull(message = "Source warehouse is required")
    @JsonAlias({"originWarehouseId", "sourceWarehouseId"})
    private Long sourceWarehouseId;

    @NotNull(message = "Destination warehouse is required")
    @JsonAlias({"destinationWarehouseId", "destWarehouseId"})
    private Long destinationWarehouseId;

    private String notes;

    @JsonAlias({"lines", "items"})
    private List<TransferItemRequest> items;

    @Data
    public static class TransferItemRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;
    }
}