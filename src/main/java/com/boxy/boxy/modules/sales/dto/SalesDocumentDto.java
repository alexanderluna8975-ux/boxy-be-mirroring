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
}
