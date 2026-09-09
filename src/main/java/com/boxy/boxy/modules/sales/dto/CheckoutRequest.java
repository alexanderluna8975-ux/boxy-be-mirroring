package com.boxy.boxy.modules.sales.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CheckoutRequest {
    private Long branchId;
    private Long warehouseId;
    private Long customerId;
    private Long quotationId;

    private String documentType = "TICKET";
    private String idempotencyKey;

    private BigDecimal discountAmount = BigDecimal.ZERO;
    private String paymentMethod;
    private BigDecimal amountTendered;

    @JsonAlias({"lines", "items"})
    private List<CheckoutItemRequest> items;

    private List<PaymentRequest> payments;

    @Data
    public static class CheckoutItemRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.0001", message = "Quantity must be > 0")
        private BigDecimal quantity;

        private BigDecimal unitPrice;
        private BigDecimal discountAmount = BigDecimal.ZERO;
        private BigDecimal taxRate = BigDecimal.ZERO;
    }

    @Data
    public static class PaymentRequest {
        private String paymentMethod;
        private BigDecimal amount;
        private String referenceCode;
    }
}
