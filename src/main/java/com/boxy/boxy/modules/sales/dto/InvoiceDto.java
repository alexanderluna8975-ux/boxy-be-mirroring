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
    private Long branchId;
    private String branchName;
    private Long warehouseId;
    private String warehouseName;
    private Long customerId;
    private String customerName;
    private String documentType;
    private String series;
    private String number;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String status;
    private String createdByName;
    private List<InvoiceItemDto> items;
    private List<PaymentDto> payments;
    private Instant createdAt;
}
