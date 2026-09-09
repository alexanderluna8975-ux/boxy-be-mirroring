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
    private String warehouseCode;
    private Long productId;
    private String productSku;
    private String sku;
    private String productName;
    private String movementType;
    private String type;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal balanceAfter;
    private String referenceType;
    private String referenceId;
    private String referenceFolio;
    private String notes;
    private String createdByName;
    private String userId;
    private Instant createdAt;
    private Instant occurredAt;
}
