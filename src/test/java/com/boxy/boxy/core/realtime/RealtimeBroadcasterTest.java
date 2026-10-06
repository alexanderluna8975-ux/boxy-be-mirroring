package com.boxy.boxy.core.realtime;

import com.boxy.boxy.core.realtime.events.SaleEvent;
import com.boxy.boxy.core.realtime.events.StockChange;
import com.boxy.boxy.core.realtime.events.StockChangedEvent;
import com.boxy.boxy.core.realtime.events.TransferEvent;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeBroadcasterTest {

    private static final long COMPANY = 4L;
    private static final long BRANCH = 10L;

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private StockLevelRepository stockLevelRepository;

    @InjectMocks private RealtimeBroadcaster broadcaster;

    private static Object[] row(long productId, String quantity) {
        return new Object[] {productId, new BigDecimal(quantity)};
    }

    @SuppressWarnings("unchecked")
    private RealtimeMessage<Object> sentTo(String destination) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(destination), captor.capture());
        return (RealtimeMessage<Object>) captor.getValue();
    }

    @Test
    void aStockChangeIsBroadcastToTheCompanyTopicWithAbsoluteTotalsReadAfterTheCommit() {
        when(stockLevelRepository.sumAvailableByProductIds(anyCollection())).thenReturn(List.<Object[]>of(row(5L, "120"), row(6L, "9")));
        when(stockLevelRepository.sumAvailableByProductIdsInBranch(anyCollection(), eq(BRANCH)))
                .thenReturn(List.<Object[]>of(row(5L, "40")));

        broadcaster.onStockChanged(new StockChangedEvent(COMPANY, 7L, BRANCH, List.of(
                new StockChange(5L, new BigDecimal("10"), new BigDecimal("30"), new BigDecimal("25")),
                new StockChange(6L, new BigDecimal("2"), new BigDecimal("4"), new BigDecimal("3"))), 7L));

        RealtimeMessage<Object> message = sentTo("/topic/company.4.stock");
        assertThat(message.type()).isEqualTo("STOCK_CHANGED");
        assertThat(message.occurredAt()).isNotBlank();
        RealtimeBroadcaster.StockPayload payload = (RealtimeBroadcaster.StockPayload) message.data();
        assertThat(payload.warehouseId()).isEqualTo(7L);
        assertThat(payload.branchId()).isEqualTo(BRANCH);

        RealtimeBroadcaster.StockItemPayload first = payload.items().get(0);
        assertThat(first.productId()).isEqualTo(5L);
        assertThat(first.warehouseAvailable()).isEqualByComparingTo("25"); // the event's "after"
        assertThat(first.branchAvailable()).isEqualByComparingTo("40");
        assertThat(first.totalAvailable()).isEqualByComparingTo("120");
        assertThat(first.minStock()).isEqualByComparingTo("10");

        // A product with no stock left in the branch reads as 0, not as "unknown".
        assertThat(payload.items().get(1).branchAvailable()).isEqualByComparingTo("0");
        assertThat(payload.items().get(1).totalAvailable()).isEqualByComparingTo("9");
    }

    @Test
    void theTotalsAreReadWithOneGroupedQueryPerScopeNotOnePerProduct() {
        when(stockLevelRepository.sumAvailableByProductIds(anyCollection())).thenReturn(List.of());
        when(stockLevelRepository.sumAvailableByProductIdsInBranch(anyCollection(), any())).thenReturn(List.of());

        broadcaster.onStockChanged(new StockChangedEvent(COMPANY, 7L, BRANCH, List.of(
                new StockChange(1L, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE),
                new StockChange(2L, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE),
                new StockChange(3L, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE)), 7L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(stockLevelRepository).sumAvailableByProductIds(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(1L, 2L, 3L);
        verify(stockLevelRepository).sumAvailableByProductIdsInBranch(anyCollection(), eq(BRANCH));
    }

    @Test
    void aSaleGoesToItsBranchTopicWithATypeNamedAfterTheEvent() {
        broadcaster.onSale(new SaleEvent(SaleEvent.Type.VOIDED, COMPANY, BRANCH, 50L, "B001-00042", 7L));

        RealtimeMessage<Object> message = sentTo("/topic/company.4.branch.10.sales");
        assertThat(message.type()).isEqualTo("SALE_VOIDED");
        assertThat(message.data()).isEqualTo(new RealtimeBroadcaster.SalePayload(50L, "B001-00042", BRANCH));
    }

    @Test
    void aTransferGoesToTheCompanyTransfersTopic() {
        broadcaster.onTransfer(new TransferEvent(TransferEvent.Type.RECEIVED, COMPANY, 9L, "TRF-9", 10L, 11L, true, 3L, 7L));

        RealtimeMessage<Object> message = sentTo("/topic/company.4.transfers");
        assertThat(message.type()).isEqualTo("TRANSFER_RECEIVED");
        assertThat(message.data()).isEqualTo(new RealtimeBroadcaster.TransferPayload(9L, "TRF-9", 10L, 11L, true));
    }

    @Test
    void theActorIsNeverPartOfAWirePayload() {
        // Who performed an action is for the server-side notification rules only; a topic is
        // company-wide, so leaking it would tell every subscriber who did what.
        broadcaster.onTransfer(new TransferEvent(TransferEvent.Type.REQUESTED, COMPANY, 9L, "TRF-9", 10L, 11L, false, 3L, 7L));

        String payload = sentTo("/topic/company.4.transfers").data().toString();
        assertThat(payload).doesNotContain("actor").doesNotContain("createdBy");
    }

    @Test
    void aBroadcastFailureIsSwallowedBecauseTheBusinessOperationAlreadyCommitted() {
        when(stockLevelRepository.sumAvailableByProductIds(anyCollection())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> broadcaster.onStockChanged(new StockChangedEvent(COMPANY, 7L, BRANCH,
                List.of(new StockChange(1L, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE)), 7L)))
                .doesNotThrowAnyException();
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void aSendFailureIsSwallowedToo() {
        doThrow(new IllegalStateException("broker gone")).when(messagingTemplate).convertAndSend(any(String.class), any(Object.class));

        assertThatCode(() -> broadcaster.onSale(new SaleEvent(SaleEvent.Type.CREATED, COMPANY, BRANCH, 1L, "B001-1", 7L)))
                .doesNotThrowAnyException();
    }
}
