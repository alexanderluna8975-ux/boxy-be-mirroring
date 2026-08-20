package com.boxy.boxy.modules.purchasing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDto {
    private String id;
    private String branchId;
    private String branchName;
    private String supplierId;
    private String supplierName;
    private String orderNumber;
    private LocalDate issueDate;
    private LocalDate expectedDeliveryDate;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String status;
    private String notes;
    private String createdByName;
    private List<PurchaseOrderItemDto> items;
    private Instant createdAt;
}
