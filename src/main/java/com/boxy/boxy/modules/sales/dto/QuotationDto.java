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
public class QuotationDto {
    private Long id;
    private String folio;
    private Long customerId;
    private String customerName;
    private Long branchId;
    private String branchName;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal total;
    private int lineCount;
    private String notes;
    private Instant validUntil;
    private List<QuotationLineDto> lines;
    private Instant createdAt;
    /** Set once converted to a sale — the resulting Invoice's id/folio, so the
     *  detail page can link straight to its Nota de Venta. */
    private Long saleId;
    private String saleFolio;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotationLineDto {
        private Long productId;
        private String sku;
        private String productName;
        private BigDecimal unitPrice;
        private BigDecimal quantity;
        private BigDecimal lineDiscount;
        private BigDecimal lineTotal;
    }
}
