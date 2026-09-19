package com.boxy.boxy.core.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Spells a monetary amount out in Spanish, the way every printed Bolivian/Latin-American invoice
 * does — "Son: Diez Mil Cuatrocientos 00/100 Bs." Title-cased to match that convention.
 * <p>
 * Handles the integer part up to 999,999,999 (nine digits — comfortably past any real invoice
 * total) plus cents as a literal "NN/100" fraction, never spelled out.
 */
public final class AmountInWordsEs {

    private static final String[] UNITS = {
            "", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve"};
    private static final String[] TEENS = {
            "diez", "once", "doce", "trece", "catorce", "quince",
            "dieciséis", "diecisiete", "dieciocho", "diecinueve"};
    private static final String[] TWENTIES = {
            "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro",
            "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
    private static final String[] TENS = {
            "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
    private static final String[] HUNDREDS = {
            "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
            "seiscientos", "setecientos", "ochocientos", "novecientos"};

    private AmountInWordsEs() {
    }

    /** {@code "Diez Mil Cuatrocientos 00/100 Bs."} — {@code currencyLabel} is the tenant's own
     *  {@code Company.currencySymbol}/name, never hardcoded to Bolivianos. */
    public static String format(BigDecimal amount, String currencyLabel) {
        BigDecimal normalized = amount == null ? BigDecimal.ZERO : amount.abs().setScale(2, RoundingMode.HALF_UP);
        long integerPart = normalized.longValue();
        int cents = normalized.subtract(BigDecimal.valueOf(integerPart)).movePointRight(2).intValue();

        String words = integerPart == 0 ? "cero" : spell(integerPart);
        String titleCased = titleCase(words.trim().replaceAll("\\s+", " "));
        return String.format("%s %02d/100 %s", titleCased, cents, currencyLabel).trim();
    }

    private static String spell(long value) {
        if (value == 0) {
            return "";
        }
        if (value < 1_000_000_000L) {
            long millions = value / 1_000_000L;
            long remainder = value % 1_000_000L;
            StringBuilder result = new StringBuilder();
            if (millions > 0) {
                result.append(millions == 1 ? "un millón" : spell(millions) + " millones");
            }
            if (remainder > 0) {
                if (result.length() > 0) {
                    result.append(' ');
                }
                result.append(spellUnderMillion(remainder));
            }
            return result.toString();
        }
        // Anything at or past a billion is not a realistic invoice total — spell what we can
        // rather than throwing, so a runaway value still renders something instead of a 500.
        return spellUnderMillion(value % 1_000_000_000L);
    }

    private static String spellUnderMillion(long value) {
        long thousands = value / 1000L;
        long remainder = value % 1000L;
        StringBuilder result = new StringBuilder();
        if (thousands > 0) {
            result.append(thousands == 1 ? "mil" : spellUnderThousand(thousands) + " mil");
        }
        if (remainder > 0) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(spellUnderThousand(remainder));
        }
        return result.toString();
    }

    private static String spellUnderThousand(long value) {
        if (value == 100) {
            return "cien";
        }
        long hundreds = value / 100L;
        long remainder = value % 100L;
        StringBuilder result = new StringBuilder();
        if (hundreds > 0) {
            result.append(HUNDREDS[(int) hundreds]);
        }
        if (remainder > 0) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(spellUnderHundred(remainder));
        }
        return result.toString();
    }

    private static String spellUnderHundred(long value) {
        if (value < 10) {
            return UNITS[(int) value];
        }
        if (value < 20) {
            return TEENS[(int) value - 10];
        }
        if (value < 30) {
            return TWENTIES[(int) value - 20];
        }
        long tens = value / 10L;
        long units = value % 10L;
        return units == 0 ? TENS[(int) tens] : TENS[(int) tens] + " y " + UNITS[(int) units];
    }

    private static String titleCase(String words) {
        StringBuilder result = new StringBuilder(words.length());
        boolean capitalizeNext = true;
        for (char c : words.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
