package com.boxy.boxy.core.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String sku, String productName, double available, double requested) {
        super("INSUFFICIENT_STOCK",
                String.format("Insufficient stock for product '%s' (%s). Available: %.2f, Requested: %.2f",
                        productName, sku, available, requested),
                HttpStatus.CONFLICT);
    }

    public InsufficientStockException(String message) {
        super("INSUFFICIENT_STOCK", message, HttpStatus.CONFLICT);
    }
}
