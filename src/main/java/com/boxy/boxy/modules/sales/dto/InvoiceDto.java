package com.boxy.boxy.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDto {
    private Long id;
    private String folio;
    private Long branchId;
    private String branchName;
    private Long warehouseId;
    private String warehouseName;
    private Long customerId;
    private String customerName;
    private Long quotationId;
    private String documentType;
    private String series;
    private String number;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal roundingAdjustment;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal total;
    private String status;
    private String paymentMethod;
    private BigDecimal amountTendered;
    private BigDecimal changeDue;
    private String createdByName;
    private String soldBy;
    private Instant soldAt;
    private List<InvoiceItemDto> items;
    private List<InvoiceItemDto> lines;
    private List<PaymentDto> payments;
    private BigDecimal amountPaid;
    private BigDecimal balanceDue;
    /** Agreed credit term in days, captured at checkout. {@code null} for every non-credit sale. */
    private Integer creditTermDays;
    /** {@code soldAt + creditTermDays}, derived server-side. {@code null} for every non-credit sale. */
    private Instant dueDate;
    private Instant voidedAt;
    private String voidedBy;
    private Instant createdAt;
}
