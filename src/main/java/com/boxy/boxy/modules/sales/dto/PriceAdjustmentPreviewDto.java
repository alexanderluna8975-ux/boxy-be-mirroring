package com.boxy.boxy.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** What an adjustment would do, without applying it: one page of lines plus totals over all of them. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAdjustmentPreviewDto {
    private List<PriceAdjustmentLineDto> lines;
    /** Products that would be repriced (all pages). */
    private long totalCount;
    /** Products left out because the formula needs a cost they don't have. */
    private long skippedCount;
    private List<String> skippedSample;
    private BigDecimal totalBefore;
    private BigDecimal totalAfter;
    private BigDecimal averageDeltaPercent;
}
