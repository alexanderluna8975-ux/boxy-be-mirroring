package com.boxy.boxy.modules.sales.service;

import com.boxy.boxy.modules.sales.dto.CashierSessionDetailDto;
import com.boxy.boxy.modules.sales.dto.CashierSessionDetailDto.MethodTotals;
import com.boxy.boxy.modules.sales.dto.CashierSessionDetailDto.Operation;
import com.boxy.boxy.modules.sales.dto.CashierSessionDto;
import com.boxy.boxy.modules.sales.entity.Invoice;
import com.boxy.boxy.modules.sales.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the closing detail of a cash register: income and expenses by payment method plus the
 * chronological list of operations.
 * <ul>
 *   <li><b>Income</b> — the payments of the register's sales that are still standing, the credit sales
 *   (their total, owed rather than collected) and the collections recorded against any sale while
 *   this register was open.</li>
 *   <li><b>Expenses</b> — sales voided while the register was open: the money that went back out.</li>
 * </ul>
 * Pure (no repositories), so the rules are testable without a database.
 */
final class CashierSessionDetailAssembler {

    private CashierSessionDetailAssembler() {
    }

    static CashierSessionDetailDto assemble(CashierSessionDto register, List<Invoice> invoices,
                                            List<Payment> collections, Instant windowEnd) {
        Totals income = new Totals();
        Totals expenses = new Totals();
        List<Operation> operations = new ArrayList<>();

        for (Invoice invoice : invoices) {
            boolean voided = "VOIDED".equalsIgnoreCase(invoice.getStatus());
            boolean voidedInWindow = voided && invoice.getVoidedAt() != null
                    && (windowEnd == null || !invoice.getVoidedAt().isAfter(windowEnd));
            String saleMethod = methodKey(invoice.getPaymentMethod());
            BigDecimal total = amountOf(invoice.getTotalAmount());

            operations.add(Operation.builder()
                    .type("SALE").at(invoice.getCreatedAt()).folio(folioOf(invoice)).saleId(invoice.getId())
                    .customerName(customerNameOf(invoice)).method(saleMethod).amount(total)
                    .note(voided ? "Anulada" : null).build());

            if (voided) {
                // A voided sale is not income; if it was voided while this register was open its money went back out.
                if (voidedInWindow) {
                    spread(expenses, invoice, saleMethod, total);
                    operations.add(Operation.builder()
                            .type("VOID").at(invoice.getVoidedAt()).folio(folioOf(invoice)).saleId(invoice.getId())
                            .customerName(customerNameOf(invoice)).method(saleMethod).amount(total.negate())
                            .note(invoice.getVoidReason()).build());
                }
            } else {
                spread(income, invoice, saleMethod, total);
            }
        }

        for (Payment collection : collections) {
            Invoice invoice = collection.getInvoice();
            if (invoice != null && "VOIDED".equalsIgnoreCase(invoice.getStatus())) {
                continue;
            }
            String method = methodKey(collection.getPaymentMethod());
            BigDecimal amount = amountOf(collection.getAmount());
            income.add(method, amount);
            operations.add(Operation.builder()
                    .type("PAYMENT").at(collection.getCreatedAt())
                    .folio(invoice != null ? folioOf(invoice) : null).saleId(invoice != null ? invoice.getId() : null)
                    .customerName(invoice != null ? customerNameOf(invoice) : null).method(method).amount(amount)
                    .note("Cobro a cuenta").build());
        }

        operations.sort(Comparator.comparing(Operation::getAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return CashierSessionDetailDto.builder()
                .register(register).income(income.toDto()).expenses(expenses.toDto()).operations(operations).build();
    }

    /** What a sale puts into a Totals: its payments by method, or — for a credit sale — its total as credit. */
    private static void spread(Totals totals, Invoice invoice, String saleMethod, BigDecimal total) {
        if ("credit".equals(saleMethod)) {
            totals.add("credit", total);
            return;
        }
        for (Payment payment : invoice.getPayments()) {
            totals.add(methodKey(payment.getPaymentMethod()), amountOf(payment.getAmount()));
        }
    }

    static String methodKey(String raw) {
        if (raw == null) {
            return "other";
        }
        String method = raw.trim().toUpperCase();
        if (method.equals("CASH")) {
            return "cash";
        }
        if (method.contains("CARD")) {
            return "card";
        }
        if (method.contains("TRANSFER") || method.equals("QR")) {
            return "transfer";
        }
        if (method.equals("CREDIT")) {
            return "credit";
        }
        return "other";
    }

    private static String folioOf(Invoice invoice) {
        return invoice.getSeries() != null && invoice.getNumber() != null
                ? invoice.getSeries() + "-" + invoice.getNumber()
                : "V-" + String.format("%05d", invoice.getId());
    }

    private static String customerNameOf(Invoice invoice) {
        return invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Cliente General";
    }

    private static BigDecimal amountOf(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /** Running totals per method. */
    private static final class Totals {
        private BigDecimal cash = BigDecimal.ZERO;
        private BigDecimal card = BigDecimal.ZERO;
        private BigDecimal transfer = BigDecimal.ZERO;
        private BigDecimal credit = BigDecimal.ZERO;
        private BigDecimal other = BigDecimal.ZERO;

        void add(String method, BigDecimal amount) {
            switch (method) {
                case "cash" -> cash = cash.add(amount);
                case "card" -> card = card.add(amount);
                case "transfer" -> transfer = transfer.add(amount);
                case "credit" -> credit = credit.add(amount);
                default -> other = other.add(amount);
            }
        }

        MethodTotals toDto() {
            return MethodTotals.builder().cash(cash).card(card).transfer(transfer).credit(credit).other(other)
                    .total(cash.add(card).add(transfer).add(credit).add(other)).build();
        }
    }
}
