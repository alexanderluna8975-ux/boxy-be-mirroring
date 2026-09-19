package com.boxy.boxy.core.pdf;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsEsTest {

    @Test
    void matchesTheReferenceInvoiceExactly() {
        // The literal example this class was built to reproduce.
        assertThat(AmountInWordsEs.format(new BigDecimal("10400.00"), "Bs."))
                .isEqualTo("Diez Mil Cuatrocientos 00/100 Bs.");
    }

    @Test
    void zero() {
        assertThat(AmountInWordsEs.format(BigDecimal.ZERO, "Bs.")).isEqualTo("Cero 00/100 Bs.");
    }

    @Test
    void singleDigit() {
        assertThat(AmountInWordsEs.format(new BigDecimal("1"), "Bs.")).isEqualTo("Uno 00/100 Bs.");
    }

    @Test
    void teens() {
        assertThat(AmountInWordsEs.format(new BigDecimal("15"), "Bs.")).isEqualTo("Quince 00/100 Bs.");
    }

    @Test
    void twentiesUseTheContractedForm() {
        assertThat(AmountInWordsEs.format(new BigDecimal("21"), "Bs.")).isEqualTo("Veintiuno 00/100 Bs.");
        assertThat(AmountInWordsEs.format(new BigDecimal("29"), "Bs.")).isEqualTo("Veintinueve 00/100 Bs.");
    }

    @Test
    void tensAboveTwentyUseY() {
        assertThat(AmountInWordsEs.format(new BigDecimal("31"), "Bs.")).isEqualTo("Treinta Y Uno 00/100 Bs.");
        assertThat(AmountInWordsEs.format(new BigDecimal("99"), "Bs.")).isEqualTo("Noventa Y Nueve 00/100 Bs.");
    }

    @Test
    void exactlyOneHundredIsCien() {
        assertThat(AmountInWordsEs.format(new BigDecimal("100"), "Bs.")).isEqualTo("Cien 00/100 Bs.");
    }

    @Test
    void oneHundredAndOneIsCientoUno() {
        assertThat(AmountInWordsEs.format(new BigDecimal("101"), "Bs.")).isEqualTo("Ciento Uno 00/100 Bs.");
    }

    @Test
    void hundredsFamily() {
        assertThat(AmountInWordsEs.format(new BigDecimal("200"), "Bs.")).isEqualTo("Doscientos 00/100 Bs.");
        assertThat(AmountInWordsEs.format(new BigDecimal("500"), "Bs.")).isEqualTo("Quinientos 00/100 Bs.");
        assertThat(AmountInWordsEs.format(new BigDecimal("999"), "Bs."))
                .isEqualTo("Novecientos Noventa Y Nueve 00/100 Bs.");
    }

    @Test
    void exactlyOneThousandHasNoLeadingUno() {
        assertThat(AmountInWordsEs.format(new BigDecimal("1000"), "Bs.")).isEqualTo("Mil 00/100 Bs.");
    }

    @Test
    void thousandsFamily() {
        assertThat(AmountInWordsEs.format(new BigDecimal("2500"), "Bs.")).isEqualTo("Dos Mil Quinientos 00/100 Bs.");
    }

    @Test
    void exactlyOneMillionIsUnMillon() {
        assertThat(AmountInWordsEs.format(new BigDecimal("1000000"), "Bs.")).isEqualTo("Un Millón 00/100 Bs.");
    }

    @Test
    void millionsUsePluralForm() {
        assertThat(AmountInWordsEs.format(new BigDecimal("2000000"), "Bs.")).isEqualTo("Dos Millones 00/100 Bs.");
    }

    @Test
    void millionsCombineWithThousandsAndUnits() {
        assertThat(AmountInWordsEs.format(new BigDecimal("1234567"), "Bs."))
                .isEqualTo("Un Millón Doscientos Treinta Y Cuatro Mil Quinientos Sesenta Y Siete 00/100 Bs.");
    }

    @Test
    void centsAreNeverSpelledOut() {
        assertThat(AmountInWordsEs.format(new BigDecimal("42.75"), "Bs.")).isEqualTo("Cuarenta Y Dos 75/100 Bs.");
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        assertThat(AmountInWordsEs.format(new BigDecimal("10.005"), "Bs.")).isEqualTo("Diez 01/100 Bs.");
    }

    @Test
    void negativeAmountsAreSpelledAsTheirAbsoluteValue() {
        // A refund/credit-note total might arrive negative; the words themselves are never signed.
        assertThat(AmountInWordsEs.format(new BigDecimal("-500"), "Bs.")).isEqualTo("Quinientos 00/100 Bs.");
    }

    @Test
    void nullAmountIsTreatedAsZero() {
        assertThat(AmountInWordsEs.format(null, "Bs.")).isEqualTo("Cero 00/100 Bs.");
    }

    @Test
    void honorsTheTenantsOwnCurrencyLabel_notHardcodedBolivianos() {
        assertThat(AmountInWordsEs.format(new BigDecimal("50"), "USD")).isEqualTo("Cincuenta 00/100 USD");
    }
}
