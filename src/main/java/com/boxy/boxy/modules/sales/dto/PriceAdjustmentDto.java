package com.boxy.boxy.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAdjustmentDto {
    private Long id;
    private String folio;
    private String tariff;
    private String unit;
    private BigDecimal amount;
    private String basedOn;
    private String roundingMode;
    private String notes;
    private List<PriceAdjustmentLineDto> lines;
    private String appliedBy;
    private Instant appliedAt;
    private int lineCount;
    private BigDecimal averageDeltaPercent;
}
