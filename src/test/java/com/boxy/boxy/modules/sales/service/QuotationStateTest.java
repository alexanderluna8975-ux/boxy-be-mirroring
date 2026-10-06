package com.boxy.boxy.modules.sales.service;

import com.boxy.boxy.modules.sales.entity.Invoice;
import com.boxy.boxy.modules.sales.entity.SalesOrder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The three states the Cotizaciones screen speaks in, derived from the raw lifecycle + resulting sale. */
class QuotationStateTest {

    private static SalesOrder quotation(String status) {
        SalesOrder so = new SalesOrder();
        so.setStatus(status);
        return so;
    }

    private static Invoice invoice(String status) {
        Invoice invoice = new Invoice();
        invoice.setStatus(status);
        return invoice;
    }

    @Test
    void aQuotationThatBecameASaleIsAccepted() {
        assertThat(SalesService.quotationState(quotation("converted"), invoice("ISSUED"))).isEqualTo("accepted");
    }

    @Test
    void aQuotationWhoseSaleWasVoidedIsRejected() {
        assertThat(SalesService.quotationState(quotation("converted"), invoice("VOIDED"))).isEqualTo("rejected");
    }

    @Test
    void aCancelledOrRejectedQuotationIsRejected() {
        assertThat(SalesService.quotationState(quotation("cancelled"), null)).isEqualTo("rejected");
        assertThat(SalesService.quotationState(quotation("rejected"), null)).isEqualTo("rejected");
    }

    @Test
    void aQuotationStillWithTheCustomerIsPending() {
        assertThat(SalesService.quotationState(quotation("sent"), null)).isEqualTo("pending");
        assertThat(SalesService.quotationState(quotation("draft"), null)).isEqualTo("pending");
        assertThat(SalesService.quotationState(quotation("approved"), null)).isEqualTo("pending");
    }

    @Test
    void aConvertedQuotationWithNoSaleOnRecordIsStillAccepted() {
        assertThat(SalesService.quotationState(quotation("converted"), null)).isEqualTo("accepted");
    }
}
