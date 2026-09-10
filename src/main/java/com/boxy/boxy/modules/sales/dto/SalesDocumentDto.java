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
}
