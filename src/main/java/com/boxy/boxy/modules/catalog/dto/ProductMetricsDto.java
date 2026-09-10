package com.boxy.boxy.modules.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Server-computed figures for the product detail hero + KPI row. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMetricsDto {
    private BigDecimal availableStock;
    private BigDecimal inTransitStock;
    private BigDecimal inventoryValue;
    private BigDecimal minStock;
    private BigDecimal averageCost;
    /** Null when {@code sellingPrice} is 0 — margin is undefined, not zero, in that case. */
    private BigDecimal marginPercent;
}
