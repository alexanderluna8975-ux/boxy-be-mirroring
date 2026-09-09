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
    private Long id;
    private Long productId;
    private String productSku;
    private String sku;
    private String productName;
    private String name;
    private BigDecimal quantityOrdered;
    private BigDecimal quantityReceived;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal totalCost;
    private BigDecimal lineTotal;
}
