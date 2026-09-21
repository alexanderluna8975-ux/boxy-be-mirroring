package com.boxy.boxy.modules.catalog.dto;

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
public class ProductCostHistoryDto {
    private Long id;
    private BigDecimal previousCost;
    private BigDecimal newCost;
    private BigDecimal unitCost;
    private BigDecimal quantityReceived;
    private String goodsReceiptNumber;
    private String createdByName;
    private Instant createdAt;
}
