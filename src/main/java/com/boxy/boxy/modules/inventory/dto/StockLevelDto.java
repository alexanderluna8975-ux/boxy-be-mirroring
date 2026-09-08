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
public class StockLevelDto {
    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private Long branchId;
    private String branchName;
    private Long productId;
    private String productSku;
    private String productName;
    private BigDecimal quantityAvailable;
    private BigDecimal quantityReserved;
    private BigDecimal quantityInTransit;
    private BigDecimal minStockAlert;
    private boolean isLowStock;
    private Instant updatedAt;
}
