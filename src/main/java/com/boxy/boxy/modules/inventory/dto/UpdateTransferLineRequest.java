package com.boxy.boxy.modules.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Body for {@code PATCH /transfers/{id}/lines/{productId}} — corrects a single line's mistyped
 * quantity, inline, before approval. Only REQUESTED transfers accept this (see
 * {@code InventoryService#updateTransferLine}): once approved, stock has already moved.
 */
@Data
public class UpdateTransferLineRequest {
    @NotNull(message = "Quantity is required")
    private BigDecimal quantity;
}
