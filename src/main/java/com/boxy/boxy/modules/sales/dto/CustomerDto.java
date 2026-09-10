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
public class CustomerDto {
    private Long id;
    private String code;
    private String name;
    private String taxId;
    private String type;
    private String documentType;
    private String documentNumber;
    private String email;
    private String phone;
    private String address;
    private Long branchId;
    private String branchName;
    private String status;
    private BigDecimal creditLimit;
    private BigDecimal currentCredit;
    private int quotationCount;
    private int saleCount;
    private BigDecimal totalPurchased;
    private Instant lastPurchaseAt;
    private boolean isActive;
    private Instant createdAt;
}
