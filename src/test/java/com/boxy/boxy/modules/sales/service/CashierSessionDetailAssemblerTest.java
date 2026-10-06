package com.boxy.boxy.modules.sales.service;

import com.boxy.boxy.modules.sales.dto.CashierSessionDetailDto;
import com.boxy.boxy.modules.sales.dto.CashierSessionDto;
import com.boxy.boxy.modules.sales.entity.Customer;
import com.boxy.boxy.modules.sales.entity.Invoice;
import com.boxy.boxy.modules.sales.entity.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CashierSessionDetailAssemblerTest {

    private static final Instant T0 = Instant.parse("2026-10-06T12:00:00Z");

    private static Invoice invoice(long id, String method, String total, String status, Instant createdAt) {
        return Invoice.builder().id(id).series("B001").number(String.format("%05d", id))
                .paymentMethod(method).totalAmount(new BigDecimal(total)).status(status).createdAt(createdAt)
                .customer(Customer.builder().id(1L).name("Cliente").build()).build();
    }

    private static Payment payment(Invoice invoice, String method, String amount) {
        Payment p = Payment.builder().invoice(invoice).paymentMethod(method).amount(new BigDecimal(amount)).build();
        invoice.getPayments().add(p);
        return p;
    }

    private static CashierSessionDetailDto assemble(List<Invoice> invoices, List<Payment> collections, Instant end) {
        return CashierSessionDetailAssembler.assemble(CashierSessionDto.builder().id(1L).build(), invoices, collections, end);
    }

    @Test
    void incomeIsSplitByMethodAndTotalled() {
        Invoice cash = invoice(1, "cash", "60", "ISSUED", T0);
        payment(cash, "CASH", "60");
        Invoice qr = invoice(2, "transfer", "40", "ISSUED", T0.plusSeconds(60));
        payment(qr, "TRANSFER", "40");

        var detail = assemble(List.of(cash, qr), List.of(), null);

        assertThat(detail.getIncome().getCash()).isEqualByComparingTo("60");
        assertThat(detail.getIncome().getTransfer()).isEqualByComparingTo("40");
        assertThat(detail.getIncome().getTotal()).isEqualByComparingTo("100");
        assertThat(detail.getExpenses().getTotal()).isEqualByComparingTo("0");
    }

    @Test
    void aCreditSaleCountsAsCreditNotAsCollectedMoney() {
        Invoice credit = invoice(1, "credit", "200", "ISSUED", T0);

        var detail = assemble(List.of(credit), List.of(), null);

        assertThat(detail.getIncome().getCredit()).isEqualByComparingTo("200");
        assertThat(detail.getIncome().getCash()).isEqualByComparingTo("0");
    }

    @Test
    void aSaleVoidedWhileTheRegisterWasOpenMovesFromIncomeToExpenses() {
        Invoice sale = invoice(1, "cash", "60", "VOIDED", T0);
        sale.setVoidedAt(T0.plusSeconds(600));
        sale.setVoidReason("Error");
        payment(sale, "CASH", "60");

        var detail = assemble(List.of(sale), List.of(), T0.plusSeconds(3600));

        assertThat(detail.getIncome().getTotal()).isEqualByComparingTo("0");
        assertThat(detail.getExpenses().getCash()).isEqualByComparingTo("60");
        assertThat(detail.getOperations()).extracting("type").containsExactly("SALE", "VOID");
        assertThat(detail.getOperations().get(1).getAmount()).isEqualByComparingTo("-60");
        assertThat(detail.getOperations().get(1).getNote()).isEqualTo("Error");
    }

    @Test
    void aSaleVoidedAfterTheRegisterClosedIsNotAnExpenseOfIt() {
        Invoice sale = invoice(1, "cash", "60", "VOIDED", T0);
        sale.setVoidedAt(T0.plusSeconds(7200));
        payment(sale, "CASH", "60");

        var detail = assemble(List.of(sale), List.of(), T0.plusSeconds(3600));

        assertThat(detail.getExpenses().getTotal()).isEqualByComparingTo("0");
        assertThat(detail.getOperations()).extracting("type").containsExactly("SALE");
    }

    @Test
    void collectionsRecordedInTheRegisterAreIncomeAndListedAsOperations() {
        Invoice older = invoice(1, "credit", "200", "ISSUED", T0.minusSeconds(86400));
        Payment collection = payment(older, "CASH", "50");

        var detail = assemble(List.of(), List.of(collection), null);

        assertThat(detail.getIncome().getCash()).isEqualByComparingTo("50");
        assertThat(detail.getOperations()).extracting("type").containsExactly("PAYMENT");
    }

    @Test
    void methodsAreNormalised() {
        assertThat(CashierSessionDetailAssembler.methodKey("CASH")).isEqualTo("cash");
        assertThat(CashierSessionDetailAssembler.methodKey("CREDIT_CARD")).isEqualTo("card");
        assertThat(CashierSessionDetailAssembler.methodKey("bank_transfer")).isEqualTo("transfer");
        assertThat(CashierSessionDetailAssembler.methodKey("QR")).isEqualTo("transfer");
        assertThat(CashierSessionDetailAssembler.methodKey("STORE_CREDIT")).isEqualTo("other");
        assertThat(CashierSessionDetailAssembler.methodKey(null)).isEqualTo("other");
    }
}
