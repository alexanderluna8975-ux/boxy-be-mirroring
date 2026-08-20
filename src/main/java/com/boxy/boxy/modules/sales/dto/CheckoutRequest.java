package com.boxy.boxy.modules.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CheckoutRequest {
    @NotBlank(message = "Branch ID is required")
    private String branchId;

    @NotBlank(message = "Warehouse ID is required")
    private String warehouseId;

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotBlank(message = "Document type is required (e.g. TICKET, INVOICE)")
    private String documentType;

    private String idempotencyKey;

    @NotEmpty(message = "Cart cannot be empty")
    private List<CheckoutItemRequest> items;

    @NotEmpty(message = "Payment details are required")
    private List<PaymentRequest> payments;

    @Data
    public static class CheckoutItemRequest {
        @NotBlank(message = "Product ID is required")
        private String productId;

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.0001", message = "Quantity must be > 0")
        private BigDecimal quantity;

        @NotNull(message = "Unit price is required")
        private BigDecimal unitPrice;

        private BigDecimal discountAmount = BigDecimal.ZERO;
        private BigDecimal taxRate = BigDecimal.ZERO;
    }

    @Data
    public static class PaymentRequest {
        @NotBlank(message = "Payment method is required")
        private String paymentMethod; // CASH, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, STORE_CREDIT

        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be > 0")
        private BigDecimal amount;

        private String referenceCode;
    }
}
