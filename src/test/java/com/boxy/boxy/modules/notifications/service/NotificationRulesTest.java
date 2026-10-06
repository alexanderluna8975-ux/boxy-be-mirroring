package com.boxy.boxy.modules.notifications.service;

import com.boxy.boxy.core.realtime.events.SaleEvent;
import com.boxy.boxy.core.realtime.events.StockChange;
import com.boxy.boxy.core.realtime.events.StockChangedEvent;
import com.boxy.boxy.core.realtime.events.TransferEvent;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.notifications.repository.NotificationRecipientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRulesTest {

    private static final long COMPANY = 4L;
    private static final long BRANCH = 10L;
    private static final long OTHER_BRANCH = 11L;
    private static final long ACTOR = 7L;

    @Mock private NotificationService notificationService;
    @Mock private NotificationRecipientRepository recipientRepository;
    @Mock private ProductRepository productRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private com.boxy.boxy.modules.administration.repository.UserRepository userRepository;

    @InjectMocks private NotificationRules rules;

    private static StockChange change(long productId, String min, String before, String after) {
        return new StockChange(productId, min == null ? null : new BigDecimal(min), new BigDecimal(before), new BigDecimal(after));
    }

    private static StockChangedEvent stockEvent(StockChange... changes) {
        return new StockChangedEvent(COMPANY, 3L, BRANCH, List.of(changes), ACTOR);
    }

    private void stubStockLookups(long... productIds) {
        List<Product> products = new ArrayList<>();
        for (long id : productIds) {
            products.add(Product.builder().id(id).sku("SKU-" + id).name("Producto " + id).build());
        }
        when(productRepository.findAllById(anyCollection())).thenReturn(products);
        when(warehouseRepository.findById(3L)).thenReturn(Optional.of(Warehouse.builder().id(3L).name("Almacén Central").build()));
    }

    private NotificationService.Spec singleNotification(Collection<Long> expectedRecipients) {
        ArgumentCaptor<NotificationService.Spec> spec = ArgumentCaptor.forClass(NotificationService.Spec.class);
        ArgumentCaptor<Collection<Long>> recipients = ArgumentCaptor.forClass(Collection.class);
        verify(notificationService).notifyUsers(eq(COMPANY), recipients.capture(), spec.capture());
        assertThat(recipients.getValue()).containsExactlyInAnyOrderElementsOf(expectedRecipients);
        return spec.getValue();
    }

    // ---- stock ----

    @Test
    void crossingTheReorderMinimumDownwardsWarnsInventoryUsersOfThatBranchExceptTheActor() {
        when(recipientRepository.findUserIdsWithPermissionInBranch(eq(COMPANY), eq(BRANCH), anyCollection()))
                .thenReturn(List.of(ACTOR, 21L, 22L));
        stubStockLookups(5L);

        rules.onStockChanged(stockEvent(change(5L, "10", "12", "8")));

        NotificationService.Spec spec = singleNotification(Set.of(21L, 22L));
        assertThat(spec.type()).isEqualTo("LOW_STOCK");
        assertThat(spec.severity()).isEqualTo("warning");
        assertThat(spec.message()).contains("Producto 5").contains("SKU-5").contains("Almacén Central")
                .contains("8").contains("mínimo 10");
        assertThat(spec.link()).isEqualTo("/inventory/products/5");
    }

    @Test
    void aProductAlreadyBelowItsMinimumDoesNotAlertAgainOnTheNextSale() {
        rules.onStockChanged(stockEvent(change(5L, "10", "8", "7")));

        verifyNoInteractions(notificationService, recipientRepository);
    }

    @Test
    void goingUpNeverNotifies() {
        rules.onStockChanged(stockEvent(change(5L, "10", "3", "50")));

        verifyNoInteractions(notificationService);
    }

    @Test
    void runningOutIsADangerAlert() {
        when(recipientRepository.findUserIdsWithPermissionInBranch(anyLong(), anyLong(), anyCollection())).thenReturn(List.of(21L));
        stubStockLookups(5L);

        rules.onStockChanged(stockEvent(change(5L, "10", "4", "0")));

        NotificationService.Spec spec = singleNotification(Set.of(21L));
        assertThat(spec.type()).isEqualTo("OUT_OF_STOCK");
        assertThat(spec.severity()).isEqualTo("danger");
    }

    @Test
    void droppingStraightFromAboveTheMinimumToZeroIsOneOutOfStockAlertNotTwo() {
        when(recipientRepository.findUserIdsWithPermissionInBranch(anyLong(), anyLong(), anyCollection())).thenReturn(List.of(21L));
        stubStockLookups(5L);

        rules.onStockChanged(stockEvent(change(5L, "10", "30", "0")));

        assertThat(singleNotification(Set.of(21L)).type()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void aProductWithNoMinimumConfiguredHasNoLowStateButStillAlertsWhenItRunsOut() {
        rules.onStockChanged(stockEvent(change(5L, "0", "9", "2")));
        rules.onStockChanged(stockEvent(change(6L, null, "9", "2")));
        verifyNoInteractions(notificationService);

        when(recipientRepository.findUserIdsWithPermissionInBranch(anyLong(), anyLong(), anyCollection())).thenReturn(List.of(21L));
        stubStockLookups(5L);
        rules.onStockChanged(stockEvent(change(5L, "0", "2", "0")));
        assertThat(singleNotification(Set.of(21L)).type()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void aBurstOfCrossingsCollapsesIntoOneSummaryNotification() {
        when(recipientRepository.findUserIdsWithPermissionInBranch(anyLong(), anyLong(), anyCollection())).thenReturn(List.of(21L));
        stubStockLookups();
        StockChange[] many = IntStream.rangeClosed(1, 8).mapToObj(i -> change(i, "10", "12", "8")).toArray(StockChange[]::new);

        rules.onStockChanged(stockEvent(many));

        NotificationService.Spec spec = singleNotification(Set.of(21L));
        assertThat(spec.title()).startsWith("8 ");
        assertThat(spec.link()).isEqualTo("/inventory/products");
    }

    @Test
    void nobodyToTellMeansNoLookupsAndNoNotification() {
        when(recipientRepository.findUserIdsWithPermissionInBranch(anyLong(), anyLong(), anyCollection())).thenReturn(List.of(ACTOR));

        rules.onStockChanged(stockEvent(change(5L, "10", "12", "8")));

        verify(notificationService, never()).notifyUsers(any(), any(), any());
        verifyNoInteractions(productRepository);
    }

    @Test
    void aFailureWhileEvaluatingIsSwallowed() {
        when(recipientRepository.findUserIdsWithPermissionInBranch(anyLong(), anyLong(), anyCollection()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> rules.onStockChanged(stockEvent(change(5L, "10", "12", "8")))).doesNotThrowAnyException();
    }

    // ---- transfers ----

    private static TransferEvent transfer(TransferEvent.Type type, boolean hasLoss) {
        return new TransferEvent(type, COMPANY, 9L, "TRF-9", BRANCH, OTHER_BRANCH, hasLoss, 33L, ACTOR);
    }

    @Test
    void aRequestedTransferAsksTheOriginBranchApproversExceptTheRequester() {
        when(recipientRepository.findUserIdsWithPermissionInBranch(eq(COMPANY), eq(BRANCH), anyCollection()))
                .thenReturn(List.of(ACTOR, 41L));

        rules.onTransfer(transfer(TransferEvent.Type.REQUESTED, false));

        NotificationService.Spec spec = singleNotification(Set.of(41L));
        assertThat(spec.type()).isEqualTo("TRANSFER_REQUESTED");
        assertThat(spec.link()).isEqualTo("/inventory/transfers/9");
    }

    @Test
    void aDispatchedTransferTellsTheDestinationBranch() {
        when(recipientRepository.findUserIdsWithPermissionInBranch(eq(COMPANY), eq(OTHER_BRANCH), anyCollection()))
                .thenReturn(List.of(51L));

        rules.onTransfer(transfer(TransferEvent.Type.DISPATCHED, false));

        assertThat(singleNotification(Set.of(51L)).type()).isEqualTo("TRANSFER_DISPATCHED");
    }

    @Test
    void aReceivedTransferTellsTheOriginBranchAndWarnsWhenSomethingWentMissing() {
        when(recipientRepository.findUserIdsWithPermissionInBranch(eq(COMPANY), eq(BRANCH), anyCollection()))
                .thenReturn(List.of(41L));

        rules.onTransfer(transfer(TransferEvent.Type.RECEIVED, true));

        NotificationService.Spec spec = singleNotification(Set.of(41L));
        assertThat(spec.severity()).isEqualTo("warning");
        assertThat(spec.message()).contains("faltante");
    }

    @Test
    void aCleanReceiptIsOnlyInformational() {
        when(recipientRepository.findUserIdsWithPermissionInBranch(eq(COMPANY), eq(BRANCH), anyCollection()))
                .thenReturn(List.of(41L));

        rules.onTransfer(transfer(TransferEvent.Type.RECEIVED, false));

        assertThat(singleNotification(Set.of(41L)).severity()).isEqualTo("info");
    }

    @Test
    void aRejectedTransferTellsOnlyWhoRequestedIt() {
        rules.onTransfer(transfer(TransferEvent.Type.REJECTED, false));

        NotificationService.Spec spec = singleNotification(Set.of(33L));
        assertThat(spec.type()).isEqualTo("TRANSFER_REJECTED");
        verifyNoInteractions(recipientRepository);
    }

    @Test
    void rejectingYourOwnRequestNotifiesNobody() {
        rules.onTransfer(new TransferEvent(TransferEvent.Type.REJECTED, COMPANY, 9L, "TRF-9", BRANCH, OTHER_BRANCH,
                false, ACTOR, ACTOR));

        verify(notificationService).notifyUsers(eq(COMPANY), eq(Set.of()), any());
    }

    // ---- sales ----

    @Test
    void creatingASaleOrRecordingAPaymentIsNeverANotification() {
        rules.onSale(new SaleEvent(SaleEvent.Type.CREATED, COMPANY, BRANCH, 50L, "B001-1", ACTOR));
        rules.onSale(new SaleEvent(SaleEvent.Type.PAYMENT_RECORDED, COMPANY, BRANCH, 50L, "B001-1", ACTOR));

        verifyNoInteractions(notificationService, recipientRepository);
    }

    @Test
    void aVoidedSaleTellsTheBranchManagersExceptWhoVoidedIt() {
        when(recipientRepository.findUserIdsWithRoleInBranch(eq(COMPANY), eq(BRANCH), anyCollection()))
                .thenReturn(List.of(ACTOR, 61L));
        when(recipientRepository.findUserIdsWithRoleInCompany(eq(COMPANY), anyCollection())).thenReturn(List.of(62L, 61L));

        rules.onSale(new SaleEvent(SaleEvent.Type.VOIDED, COMPANY, BRANCH, 50L, "B001-00042", ACTOR));

        NotificationService.Spec spec = singleNotification(Set.of(61L, 62L));
        assertThat(spec.type()).isEqualTo("SALE_VOIDED");
        assertThat(spec.message()).contains("B001-00042");
        assertThat(spec.link()).isEqualTo("/sales/documents/50");
    }

    // ---- any audited action ----

    @Test
    void anyAuditedActionTellsSuperAdminsAndAdministratorsExceptTheActor() {
        when(recipientRepository.findUserIdsWithRoleInCompany(eq(COMPANY), anyCollection())).thenReturn(List.of(ACTOR, 71L, 72L));
        when(userRepository.findById(ACTOR)).thenReturn(Optional.of(
                com.boxy.boxy.modules.administration.entity.User.builder().id(ACTOR).firstName("Ana").lastName("Paz").build()));

        rules.onAudit(new com.boxy.boxy.core.realtime.events.AuditRecordedEvent(
                COMPANY, ACTOR, "Producto creado", "Producto", "5", "Widget"));

        NotificationService.Spec spec = singleNotification(Set.of(71L, 72L));
        assertThat(spec.message()).contains("Ana Paz").contains("Producto creado").contains("Widget");
        verify(recipientRepository).findUserIdsWithRoleInCompany(eq(COMPANY),
                eq(Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMINISTRATOR", "ROLE_ADMIN")));
    }

    @Test
    void routineLoginsAreNotNotified() {
        rules.onAudit(new com.boxy.boxy.core.realtime.events.AuditRecordedEvent(
                COMPANY, ACTOR, "Inicio de sesión exitoso", "Usuario", "7", "ana"));

        verifyNoInteractions(notificationService, recipientRepository);
    }

    @Test
    void auditNotificationsLinkToTheRecordsOwnDetailPage() {
        assertThat(NotificationRules.linkFor("Venta", "50")).isEqualTo("/sales/documents/50");
        assertThat(NotificationRules.linkFor("Transferencia", "9")).isEqualTo("/inventory/transfers/9");
        assertThat(NotificationRules.linkFor("Producto", "5")).isEqualTo("/inventory/products/5");
        assertThat(NotificationRules.linkFor("Marca", "3")).isEqualTo("/administration/settings");
        assertThat(NotificationRules.linkFor("Venta", null)).isEqualTo("/administration/audit-logs");
        assertThat(NotificationRules.linkFor("Venta", "../x")).isEqualTo("/administration/audit-logs");
    }
}
