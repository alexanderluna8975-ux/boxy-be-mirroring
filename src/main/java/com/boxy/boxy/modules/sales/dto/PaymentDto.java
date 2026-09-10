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
public class PaymentDto {
    private Long id;
    private String paymentMethod;
    private BigDecimal amount;
    private String referenceCode;
    private String note;
    private String recordedBy;
    private Instant recordedAt;
    private String status;
    private Instant createdAt;
}
