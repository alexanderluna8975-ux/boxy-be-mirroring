package com.boxy.boxy.modules.inventory.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
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
public class CreateStockAdjustmentRequest {
    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;

    private String reasonId;
    private String reason;
    private String notes;

    @JsonAlias({"lines", "items"})
    private List<AdjustmentLineRequest> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjustmentLineRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @JsonAlias({"quantityDelta", "differenceQuantity", "quantity"})
        private BigDecimal quantityDelta;
    }
}
