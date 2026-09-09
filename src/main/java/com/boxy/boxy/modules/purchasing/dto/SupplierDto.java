package com.boxy.boxy.modules.purchasing.dto;

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
public class SupplierDto {
    private Long id;
    private String taxId;
    private String name;
    private String contactName;
    private String email;
    private String phone;
    private String address;
    private int paymentTermsDays;
    private boolean isActive;
    private String status;
    private int orderCount;
    private Instant lastOrderAt;
    private BigDecimal totalPurchased;
    private Instant createdAt;
}
