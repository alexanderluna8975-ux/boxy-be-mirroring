package com.boxy.boxy.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One row of the Cuentas por Cobrar screen — every {@code paymentMethod == "credit"}
 * invoice, regardless of how much of it is still outstanding.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditSaleListItemDto {
    private Long id;
    private String folio;
    private Long customerId;
    private String customerName;
    private Instant soldAt;
    private Integer creditTermDays;
    private Instant dueDate;
    private BigDecimal total;
    private BigDecimal amountPaid;
    private BigDecimal balanceDue;
    private String status;
    /** Every distinct product on the sale — backs both the "Productos" cell and the product-code/name search. */
    private List<ProductSummaryDto> products;
    private Long branchId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSummaryDto {
        private String sku;
        private String name;
    }
}
