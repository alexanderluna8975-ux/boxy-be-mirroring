package com.boxy.boxy.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * KPI row above the Cuentas por Cobrar table/calendar — reduced over the same
 * filtered-but-unpaged set {@code getReceivables} reads from.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivablesSummaryDto {
    private BigDecimal totalOutstanding;
    private BigDecimal totalOverdue;
}
