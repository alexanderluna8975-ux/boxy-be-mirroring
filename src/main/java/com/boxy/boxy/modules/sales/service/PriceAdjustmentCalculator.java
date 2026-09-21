package com.boxy.boxy.modules.sales.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure pricing math for bulk/per-row price adjustments — mirrored exactly by
 * {@code computeAdjustedPrice}/{@code applyRounding} in the frontend's
 * {@code shared/utils/pricing.ts}, so both sides resolve the same price for
 * the same inputs.
 */
public final class PriceAdjustmentCalculator {

    private PriceAdjustmentCalculator() {
    }

    /**
     * Applies one rounding mode. {@code price_adjustment_lines.new_sale_price} is
     * DECIMAL(14,4) — every branch normalizes back to scale 4 so persisted values and
     * comparisons stay consistent. Does not floor at 0 — {@link #computeAdjustedPrice}
     * does that after, since floor-at-0 is a formula-level rule, not a rounding-mode rule.
     */
    public static BigDecimal applyRounding(BigDecimal value, String mode) {
        return switch (mode) {
            case "1-decimal-up" -> value.setScale(1, RoundingMode.HALF_EVEN).setScale(4);
            case "integer" -> value.setScale(0, RoundingMode.HALF_UP).setScale(4);
            case "charm-99" -> value.setScale(0, RoundingMode.FLOOR).add(new BigDecimal("0.99")).setScale(4);
            case "charm-90" -> value.setScale(0, RoundingMode.FLOOR).add(new BigDecimal("0.90")).setScale(4);
            default -> value.setScale(4, RoundingMode.HALF_UP); // "none"
        };
    }

    /**
     * General adjustment formula: {@code delta = unit === 'percent' ? basePrice * (amount/100)
     * : amount}; {@code raw = tariff === 'increase' ? basePrice + delta : basePrice - delta};
     * round per {@code roundingMode}; floor at 0 (a decrease can't legally go negative).
     */
    public static BigDecimal computeAdjustedPrice(
            BigDecimal basePrice, String tariff, String unit, BigDecimal amount, String roundingMode) {
        BigDecimal delta = "percent".equals(unit)
                ? basePrice.multiply(amount).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
                : amount;
        BigDecimal raw = "increase".equals(tariff) ? basePrice.add(delta) : basePrice.subtract(delta);
        BigDecimal rounded = applyRounding(raw, roundingMode);
        return rounded.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO.setScale(4) : rounded;
    }
}
