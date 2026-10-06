package com.boxy.boxy.core.realtime.events;

import java.util.List;

/**
 * Published inside the transaction that changed the stock, once per warehouse touched — a 30-line
 * sale is one event with 30 changes, not 30 events. Delivered to listeners only after the
 * transaction commits, so a rolled-back sale never reaches a screen.
 *
 * @param actorId who caused the change (null when there is no authenticated user) — used only to
 *                avoid notifying someone about their own action; it is never sent to clients.
 */
public record StockChangedEvent(Long companyId, Long warehouseId, Long branchId, List<StockChange> changes, Long actorId) {
}
