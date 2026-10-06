package com.boxy.boxy.core.realtime.events;

import java.math.BigDecimal;

/**
 * One product's stock in one warehouse before and after a transaction. Both values are kept (not
 * just a delta) because {@code NotificationService} needs to know whether the quantity
 * <i>crossed</i> the reorder threshold, which a delta alone can't tell.
 *
 * @param minStock the product's {@code minStockAlert} at the time — carried so a listener never has
 *                 to reload the product just to evaluate the threshold.
 */
public record StockChange(Long productId, BigDecimal minStock, BigDecimal availableBefore, BigDecimal availableAfter) {
}
