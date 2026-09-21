package com.boxy.boxy.modules.sales.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuotationRequest {
    @NotNull(message = "Customer ID is required")
    private Long customerId;

    private Long branchId;
    private String notes;
    private String validUntil;

    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @JsonAlias({"lines", "items"})
    private List<QuotationLineRequest> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotationLineRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        private BigDecimal unitPrice;
        private BigDecimal quantity;
        private BigDecimal lineDiscount;
    }
}
