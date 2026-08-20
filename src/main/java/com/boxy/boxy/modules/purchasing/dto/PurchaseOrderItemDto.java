package com.boxy.boxy.modules.purchasing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItemDto {
    private String id;
    private String productId;
    private String productSku;
    private String productName;
    private BigDecimal quantityOrdered;
    private BigDecimal quantityReceived;
    private BigDecimal unitCost;
    private BigDecimal taxRate;
    private BigDecimal totalCost;
}
