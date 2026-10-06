package com.boxy.boxy.core.realtime;

import com.boxy.boxy.core.realtime.events.SaleEvent;
import com.boxy.boxy.core.realtime.events.StockChange;
import com.boxy.boxy.core.realtime.events.StockChangedEvent;
import com.boxy.boxy.core.realtime.events.TransferEvent;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns domain events into WebSocket messages. Every listener runs {@code AFTER_COMMIT}: if the
 * transaction that published the event rolls back, nothing is sent, so a screen can never show a
 * sale or a stock change that didn't actually happen.
 * <p>
 * Stock messages carry <b>absolute</b> quantities (what the warehouse, the branch and the whole
 * company now hold), not "-3", read fresh at broadcast time. That makes a message safe to apply
 * twice and safe against two near-simultaneous transactions arriving out of order — the last one
 * read wins, and it is always the truth. The one extra read is two grouped queries per event
 * whatever its size, not two per product.
 * <p>
 * Best-effort like {@code AuditLogService}: by the time these run the business operation has
 * already committed, so a failure here is logged and swallowed — it must never surface as an error
 * to the user who just completed a sale.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeBroadcaster {

    public static final String STOCK_CHANGED = "STOCK_CHANGED";

    public record StockItemPayload(Long productId, BigDecimal warehouseAvailable, BigDecimal branchAvailable,
                                   BigDecimal totalAvailable, BigDecimal minStock) {}

    public record StockPayload(Long warehouseId, Long branchId, List<StockItemPayload> items) {}

    public record SalePayload(Long saleId, String saleNumber, Long branchId) {}

    public record TransferPayload(Long transferId, String transferNumber, Long originBranchId,
                                  Long destinationBranchId, boolean hasLoss) {}

    private final SimpMessagingTemplate messagingTemplate;
    private final StockLevelRepository stockLevelRepository;

    // AFTER_COMMIT means the original transaction is finished, so anything that reads the database
    // here needs a transaction of its own — REQUIRES_NEW is the form Spring allows for this.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStockChanged(StockChangedEvent event) {
        try {
            Set<Long> productIds = event.changes().stream().map(StockChange::productId).collect(Collectors.toSet());
            Map<Long, BigDecimal> totals = toMap(stockLevelRepository.sumAvailableByProductIds(productIds));
            Map<Long, BigDecimal> branchTotals =
                    toMap(stockLevelRepository.sumAvailableByProductIdsInBranch(productIds, event.branchId()));

            List<StockItemPayload> items = event.changes().stream()
                    .map(change -> new StockItemPayload(
                            change.productId(),
                            change.availableAfter(),
                            branchTotals.getOrDefault(change.productId(), BigDecimal.ZERO),
                            totals.getOrDefault(change.productId(), BigDecimal.ZERO),
                            change.minStock()))
                    .toList();

            send(RealtimeTopics.stock(event.companyId()), STOCK_CHANGED,
                    new StockPayload(event.warehouseId(), event.branchId(), items));
        } catch (Exception e) {
            log.warn("Failed to broadcast stock change for warehouse {}: {}", event.warehouseId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSale(SaleEvent event) {
        try {
            send(RealtimeTopics.sales(event.companyId(), event.branchId()), "SALE_" + event.type().name(),
                    new SalePayload(event.saleId(), event.saleNumber(), event.branchId()));
        } catch (Exception e) {
            log.warn("Failed to broadcast sale event {} for sale {}: {}", event.type(), event.saleId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransfer(TransferEvent event) {
        try {
            send(RealtimeTopics.transfers(event.companyId()), "TRANSFER_" + event.type().name(),
                    new TransferPayload(event.transferId(), event.transferNumber(), event.originBranchId(),
                            event.destinationBranchId(), event.hasLoss()));
        } catch (Exception e) {
            log.warn("Failed to broadcast transfer event {} for transfer {}: {}", event.type(), event.transferId(), e.getMessage(), e);
        }
    }

    private <T> void send(String destination, String type, T payload) {
        messagingTemplate.convertAndSend(destination, RealtimeMessage.of(type, payload));
    }

    private static Map<Long, BigDecimal> toMap(List<Object[]> rows) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }
        return map;
    }
}
