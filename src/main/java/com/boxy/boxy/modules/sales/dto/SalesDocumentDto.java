package com.boxy.boxy.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesDocumentDto {
    private String id;
    private String kind; // "sale" or "quotation"
    private String folio;
    private Instant issuedAt;
    private String customerId;
    private String customerName;
    private int lineCount;
    private BigDecimal total;
    private String paymentMethod;
    private String saleStatus;
    private BigDecimal balanceDue;
    private String quotationStatus;
    private String branchId;
    /** Set for `kind: "quotation"` only, once converted to a sale — the resulting
     *  Invoice's id, so the UI can link straight to its Nota de Venta. */
    private String saleId;
    /** Set alongside `saleId` — the resulting Invoice's human-readable folio
     *  (e.g. "B001-00007"), so the UI has something to actually display. */
    private String saleFolio;
    /** Quotations only: who made the quotation. */
    private String quotedBy;
    /** Who sold: the user who rang up the sale (for a quotation, the one who converted it — null until then). */
    private String sellerName;
    /**
     * Quotations only, the three states the screen speaks in: {@code accepted} (it became a sale),
     * {@code pending} (it is out with the customer) or {@code rejected} (it was voided, or its
     * resulting sale was). Derived from {@code quotationStatus} and the resulting sale.
     */
    private String quotationState;
    /** Quotations only: the date up to which the quote is valid. */
    private Instant validUntil;
}
