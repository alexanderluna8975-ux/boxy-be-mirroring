package com.boxy.boxy.core.realtime;

import com.boxy.boxy.core.realtime.events.SaleEvent;
import com.boxy.boxy.core.realtime.events.StockChange;
import com.boxy.boxy.core.realtime.events.StockChangedEvent;
import com.boxy.boxy.core.realtime.events.TransferEvent;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.core.security.UserPrincipal;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The one call a business service makes to say "something a screen should know about just
 * happened". It only publishes Spring application events; whether and how they reach a WebSocket or
 * a notification is decided by {@code RealtimeBroadcaster} / {@code NotificationService}, after the
 * transaction commits. Call it from inside the {@code @Transactional} method that made the change —
 * that is what lets a rollback silently discard the event.
 */
@Component
@RequiredArgsConstructor
public class RealtimeEventPublisher {

    private final ApplicationEventPublisher publisher;

    /** No-op for an empty list, so a caller can publish unconditionally after its loop. */
    public void stockChanged(Warehouse warehouse, List<StockChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        Branch branch = warehouse.getBranch();
        publisher.publishEvent(new StockChangedEvent(
                branch.getCompany().getId(), warehouse.getId(), branch.getId(), List.copyOf(changes), currentUserId()));
    }

    public void sale(SaleEvent.Type type, Long branchId, Long saleId, String saleNumber) {
        publisher.publishEvent(new SaleEvent(
                type, SecurityUtils.requireCurrentCompanyId(), branchId, saleId, saleNumber, currentUserId()));
    }

    public void transfer(TransferEvent.Type type, Long companyId, Long transferId, String transferNumber,
                         Long originBranchId, Long destinationBranchId, boolean hasLoss, Long createdByUserId) {
        publisher.publishEvent(new TransferEvent(
                type, companyId, transferId, transferNumber, originBranchId, destinationBranchId,
                hasLoss, createdByUserId, currentUserId()));
    }

    private static Long currentUserId() {
        return SecurityUtils.getCurrentUser().map(UserPrincipal::getId).orElse(null);
    }
}
