package com.boxy.boxy.modules.inventory.entity;

/**
 * Lifecycle of a {@link StockTransfer}. Valid transitions are enforced in
 * {@code InventoryService} — see the transition matrix there rather than
 * inferring one from field order here.
 */
public enum TransferStatus {
    REQUESTED,
    APPROVED,
    IN_TRANSIT,
    RECEIVED,
    REJECTED,
    CANCELLED
}
