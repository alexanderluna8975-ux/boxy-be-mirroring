package com.boxy.boxy.modules.inventory.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body for {@code PATCH /transfers/{id}/receive}. Optional and backward-compatible: an empty
 * body (or one with no {@code items}) still means "everything arrived as shipped" — every line
 * defaults to its full requested quantity — same zero-friction receive the old bodyless PATCH
 * gave when nothing went wrong. {@code notes} is only required when a line comes up short; see
 * {@code InventoryService#receiveTransfer}.
 */
@Data
public class ReceiveTransferRequest {
    @JsonAlias({"lines", "items"})
    private List<ReceiveItemRequest> items;

    private String notes;

    @Data
    public static class ReceiveItemRequest {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Received quantity is required")
        private BigDecimal quantityReceived;
    }
}
