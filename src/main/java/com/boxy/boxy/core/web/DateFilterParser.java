package com.boxy.boxy.core.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Parses the `dateFrom`/`dateTo` filter values the FE sends via
 * `dateRangeToFilters` (`shared/utils/list-query.ts`): a bare `yyyy-MM-dd` for
 * the start of the range, and a full ISO instant (with a `T23:59:59.999Z`
 * suffix already applied) for the end. Returns {@code null} for a blank or
 * unparsable value rather than throwing, so a malformed filter is silently
 * ignored instead of turning into a 500.
 */
public final class DateFilterParser {

    private DateFilterParser() {
    }

    public static Instant parseStart(String value) {
        return parse(value, false);
    }

    public static Instant parseEnd(String value) {
        return parse(value, true);
    }

    private static Instant parse(String value, boolean endOfDayIfBareDate) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignoredNotAnInstant) {
            try {
                LocalDate date = LocalDate.parse(value);
                return endOfDayIfBareDate
                        ? date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1)
                        : date.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception ignoredNotADate) {
                return null;
            }
        }
    }
}
