package com.boxy.boxy.modules.inventory.entity;

/** Lifecycle of a {@link StockAdjustment}. */
public enum AdjustmentStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CANCELLED
}
