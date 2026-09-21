package com.boxy.boxy.modules.sales.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceAdjustmentCalculatorTest {

    @Test
    void oneDecimalUpMatchesTheFourWorkedExamplesFromTheReferenceScreenshot() {
        assertThat(PriceAdjustmentCalculator.applyRounding(new BigDecimal("1.34"), "1-decimal-up"))
                .isEqualByComparingTo("1.3000");
        assertThat(PriceAdjustmentCalculator.applyRounding(new BigDecimal("1.35"), "1-decimal-up"))
                .isEqualByComparingTo("1.4000");
        assertThat(PriceAdjustmentCalculator.applyRounding(new BigDecimal("1.85"), "1-decimal-up"))
                .isEqualByComparingTo("1.8000");
        assertThat(PriceAdjustmentCalculator.applyRounding(new BigDecimal("1.86"), "1-decimal-up"))
                .isEqualByComparingTo("1.9000");
    }

    @Test
    void integerRoundsToTheNearestWholeBs() {
        assertThat(PriceAdjustmentCalculator.applyRounding(new BigDecimal("12.40"), "integer"))
                .isEqualByComparingTo("12.0000");
        assertThat(PriceAdjustmentCalculator.applyRounding(new BigDecimal("12.60"), "integer"))
                .isEqualByComparingTo("13.0000");
    }

    @Test
    void charm99And90KeepTheWholeNumberAndForceTheCents() {
        assertThat(PriceAdjustmentCalculator.applyRounding(new BigDecimal("45.00"), "charm-99"))
                .isEqualByComparingTo("45.9900");
        assertThat(PriceAdjustmentCalculator.applyRounding(new BigDecimal("45.30"), "charm-99"))
                .isEqualByComparingTo("45.9900");
        assertThat(PriceAdjustmentCalculator.applyRounding(new BigDecimal("45.00"), "charm-90"))
                .isEqualByComparingTo("45.9000");
    }

    @Test
    void noneKeepsTheExactValue() {
        assertThat(PriceAdjustmentCalculator.applyRounding(new BigDecimal("12.3456"), "none"))
                .isEqualByComparingTo("12.3456");
    }

    @Test
    void computeAdjustedPriceIncreasesByPercent() {
        BigDecimal result = PriceAdjustmentCalculator.computeAdjustedPrice(
                new BigDecimal("100"), "increase", "percent", new BigDecimal("15"), "none");
        assertThat(result).isEqualByComparingTo("115.0000");
    }

    @Test
    void computeAdjustedPriceDecreasesByPercent() {
        BigDecimal result = PriceAdjustmentCalculator.computeAdjustedPrice(
                new BigDecimal("100"), "decrease", "percent", new BigDecimal("15"), "none");
        assertThat(result).isEqualByComparingTo("85.0000");
    }

    @Test
    void computeAdjustedPriceIncreasesByFlatAmount() {
        BigDecimal result = PriceAdjustmentCalculator.computeAdjustedPrice(
                new BigDecimal("100"), "increase", "amount", new BigDecimal("20"), "none");
        assertThat(result).isEqualByComparingTo("120.0000");
    }

    @Test
    void computeAdjustedPriceDecreasesByFlatAmount() {
        BigDecimal result = PriceAdjustmentCalculator.computeAdjustedPrice(
                new BigDecimal("100"), "decrease", "amount", new BigDecimal("20"), "none");
        assertThat(result).isEqualByComparingTo("80.0000");
    }

    @Test
    void computeAdjustedPriceFloorsAtZeroWhenTheDecreaseExceedsTheBase() {
        BigDecimal result = PriceAdjustmentCalculator.computeAdjustedPrice(
                new BigDecimal("10"), "decrease", "amount", new BigDecimal("50"), "none");
        assertThat(result).isEqualByComparingTo("0.0000");
    }
}
