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
public class SaleDto {
    private Long id;
    private String folio;
    private Long customerId;
    private Long quotationId;
    private List<SaleLineDto> lines;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal total;
    private String paymentMethod;
    private BigDecimal amountTendered;
    private BigDecimal changeDue;
    private Long branchId;
    private String soldBy;
    private Instant soldAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaleLineDto {
        private Long productId;
        private String sku;
        private String name;
        private BigDecimal unitPrice;
        private BigDecimal quantity;
        private BigDecimal lineTotal;
    }
}
