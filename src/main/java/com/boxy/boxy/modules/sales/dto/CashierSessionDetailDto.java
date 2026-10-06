package com.boxy.boxy.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One cash register with everything that moved through it: what came in, what went out, and each operation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashierSessionDetailDto {
    private CashierSessionDto register;
    /** Sales and collections taken in the register, by payment method. */
    private MethodTotals income;
    /** Voided sales whose money went back out, by payment method. */
    private MethodTotals expenses;
    /** Chronological. */
    private List<Operation> operations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodTotals {
        private BigDecimal cash;
        private BigDecimal card;
        private BigDecimal transfer;
        /** Credit sales: money still owed, not collected. */
        private BigDecimal credit;
        private BigDecimal other;
        private BigDecimal total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Operation {
        /** {@code SALE}, {@code PAYMENT} (a collection on a credit sale) or {@code VOID}. */
        private String type;
        private Instant at;
        private String folio;
        private Long saleId;
        private String customerName;
        /** {@code cash}, {@code card}, {@code transfer}, {@code credit} or {@code other}. */
        private String method;
        /** Positive for money in, negative for a voided sale. */
        private BigDecimal amount;
        /** Free text: "Anulada", the void reason… */
        private String note;
    }
}
