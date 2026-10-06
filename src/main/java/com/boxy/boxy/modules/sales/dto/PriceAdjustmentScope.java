package com.boxy.boxy.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * "Every active product matching these criteria" — lets a mass adjustment name its target by rule
 * instead of shipping thousands of ids. Existence is what the user chose when building the
 * adjustment, resolved when it is applied: {@code all}, {@code in-stock} or {@code out-of-stock}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAdjustmentScope {
    private Long categoryId;
    private Long brandId;
    private String existence;
}
