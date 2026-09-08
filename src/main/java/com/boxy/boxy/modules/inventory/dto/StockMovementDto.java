package com.boxy.boxy.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementDto {
    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private Long productId;
    private String productSku;
    private String productName;
    private String movementType;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal balanceAfter;
    private String referenceType;
    private String referenceId;
    private String notes;
    private String createdByName;
    private Instant createdAt;
}
