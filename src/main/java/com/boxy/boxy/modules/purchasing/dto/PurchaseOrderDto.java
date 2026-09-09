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
    private Long id;
    private String folio;
    private String orderNumber;
    private Long branchId;
    private String branchName;
    private Long warehouseId;
    private String warehouseName;
    private Long supplierId;
    private String supplierName;
    private LocalDate issueDate;
    private LocalDate expectedDeliveryDate;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal total;
    private String status;
    private String rejectionReason;
    private String notes;
    private String createdByName;
    private int lineCount;
    private List<PurchaseOrderItemDto> items;
    private List<PurchaseOrderItemDto> lines;
    private Instant createdAt;
}
