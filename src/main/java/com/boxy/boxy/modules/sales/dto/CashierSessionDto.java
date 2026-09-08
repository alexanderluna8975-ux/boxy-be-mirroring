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
public class CashierSessionDto {
    private Long id;
    private Long branchId;
    private String branchName;
    private Long userId;
    private String userName;
    private Instant openedAt;
    private Instant closedAt;
    private BigDecimal initialCash;
    private BigDecimal expectedCash;
    private BigDecimal actualCash;
    private BigDecimal difference;
    private String status;
    private String notes;
}
