package com.boxy.boxy.modules.notifications.service;

import com.boxy.boxy.core.realtime.events.AuditRecordedEvent;
import com.boxy.boxy.core.realtime.events.SaleEvent;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.core.realtime.events.StockChange;
import com.boxy.boxy.core.realtime.events.StockChangedEvent;
import com.boxy.boxy.core.realtime.events.TransferEvent;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.notifications.repository.NotificationRecipientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The catalogue of "what is worth telling whom": every rule turns a domain event into zero or more
 * notifications. Kept apart from {@link NotificationService} (which only stores and delivers) so the
 * rules can be read — and tested — as plain decisions.
 * <p>
 * Principles applied everywhere:
 * <ul>
 *   <li><b>Never notify the person who did it</b> — they were there.</li>
 *   <li><b>Only on a change of state</b>, not while a state persists: low stock alerts when the
 *   quantity <i>crosses</i> the reorder threshold downwards, not on every sale afterwards.</li>
 *   <li><b>Aggregate a burst</b> — an import that drops 80 products below their minimum is one
 *   notification, not 80.</li>
 *   <li>Creating a sale is deliberately <i>not</i> a notification (only live data): a busy branch
 *   would bury the ones that matter.</li>
 * </ul>
 * All listeners run after the publishing transaction commits and are best-effort — see
 * {@link NotificationService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRules {

    /** More than this many crossings in one event collapse into a single summary notification. */
    static final int MAX_INDIVIDUAL_STOCK_ALERTS = 5;

    static final Set<String> INVENTORY_PERMISSIONS = Set.of("inventory:view", "inventory:read", "inventory:adjust");
    static final Set<String> TRANSFER_APPROVER_PERMISSIONS = Set.of("transfers:approve", "inventory:transfer");
    static final Set<String> TRANSFER_RECEIVER_PERMISSIONS = Set.of("transfers:receive", "inventory:transfer");
    static final Set<String> TRANSFER_VIEWER_PERMISSIONS = Set.of("transfers:view", "inventory:read");
    static final Set<String> ADMIN_ROLES = Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMINISTRATOR", "ROLE_ADMIN");
    static final Set<String> BRANCH_MANAGER_ROLES = Set.of("ROLE_ADMINISTRATOR", "ROLE_ADMIN", "ROLE_MANAGER");

    private final NotificationService notificationService;
    private final NotificationRecipientRepository recipientRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    // ---- stock ----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStockChanged(StockChangedEvent event) {
        try {
            List<StockChange> outOfStock = new ArrayList<>();
            List<StockChange> lowStock = new ArrayList<>();
            for (StockChange change : event.changes()) {
                if (crossedIntoOutOfStock(change)) {
                    outOfStock.add(change);
                } else if (crossedIntoLowStock(change)) {
                    lowStock.add(change);
                }
            }
            if (outOfStock.isEmpty() && lowStock.isEmpty()) {
                return;
            }

            Set<Long> recipients = withoutActor(
                    recipientRepository.findUserIdsWithPermissionInBranch(
                            event.companyId(), event.branchId(), INVENTORY_PERMISSIONS),
                    event.actorId());
            if (recipients.isEmpty()) {
                return;
            }

            Set<Long> productIds = new HashSet<>();
            outOfStock.forEach(c -> productIds.add(c.productId()));
            lowStock.forEach(c -> productIds.add(c.productId()));
            Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));
            String warehouseName = warehouseRepository.findById(event.warehouseId())
                    .map(Warehouse::getName).orElse("el almacén");

            announceStock(event, recipients, products, warehouseName, outOfStock, "OUT_OF_STOCK", "danger",
                    "Producto agotado", "productos agotados");
            announceStock(event, recipients, products, warehouseName, lowStock, "LOW_STOCK", "warning",
                    "Stock bajo", "productos con stock bajo");
        } catch (Exception e) {
            log.warn("Failed to evaluate stock notifications for warehouse {}: {}", event.warehouseId(), e.getMessage(), e);
        }
    }

    /** Was above zero and is now at or below it. */
    static boolean crossedIntoOutOfStock(StockChange c) {
        return c.availableBefore().signum() > 0 && c.availableAfter().signum() <= 0;
    }

    /**
     * Was above the reorder minimum and is now at or below it (but not empty — that's the out-of-stock
     * rule). A product with no minimum configured (0) has no "low" state at all.
     */
    static boolean crossedIntoLowStock(StockChange c) {
        BigDecimal min = c.minStock();
        return min != null && min.signum() > 0
                && c.availableBefore().compareTo(min) > 0
                && c.availableAfter().compareTo(min) <= 0
                && c.availableAfter().signum() > 0;
    }

    private void announceStock(StockChangedEvent event, Set<Long> recipients, Map<Long, Product> products,
                               String warehouseName, List<StockChange> crossings, String type, String severity,
                               String singularTitle, String pluralNoun) {
        if (crossings.isEmpty()) {
            return;
        }

        if (crossings.size() > MAX_INDIVIDUAL_STOCK_ALERTS) {
            notificationService.notifyUsers(event.companyId(), recipients, new NotificationService.Spec(
                    type, severity, crossings.size() + " " + pluralNoun,
                    "Revisa el inventario de " + warehouseName + ".",
                    "Almacén", String.valueOf(event.warehouseId()), "/inventory/products"));
            return;
        }

        for (StockChange change : crossings) {
            Product product = products.get(change.productId());
            String label = product != null ? "«" + product.getName() + "» (" + product.getSku() + ")" : "Un producto";
            String message = "OUT_OF_STOCK".equals(type)
                    ? label + " se agotó en " + warehouseName + "."
                    : label + " quedó en " + plain(change.availableAfter()) + " en " + warehouseName
                            + " (mínimo " + plain(change.minStock()) + ").";
            notificationService.notifyUsers(event.companyId(), recipients, new NotificationService.Spec(
                    type, severity, singularTitle, message,
                    "Producto", String.valueOf(change.productId()), "/inventory/products/" + change.productId()));
        }
    }

    // ---- transfers ----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onTransfer(TransferEvent event) {
        try {
            String link = "/inventory/transfers/" + event.transferId();
            String number = event.transferNumber();

            switch (event.type()) {
                case REQUESTED -> notifyBranch(event, event.originBranchId(), TRANSFER_APPROVER_PERMISSIONS,
                        "TRANSFER_REQUESTED", "info", "Transferencia por aprobar",
                        number + ": hay una solicitud de traspaso pendiente de tu aprobación.", link);
                case DISPATCHED -> notifyBranch(event, event.destinationBranchId(), TRANSFER_RECEIVER_PERMISSIONS,
                        "TRANSFER_DISPATCHED", "info", "Transferencia en camino",
                        number + " fue despachada hacia tu sucursal; queda pendiente de recepción.", link);
                case RECEIVED -> notifyBranch(event, event.originBranchId(), TRANSFER_VIEWER_PERMISSIONS,
                        "TRANSFER_RECEIVED", event.hasLoss() ? "warning" : "info", "Transferencia recibida",
                        event.hasLoss()
                                ? number + " fue recibida con faltante respecto a lo enviado."
                                : number + " fue recibida en destino.",
                        link);
                case REJECTED -> {
                    Set<Long> creator = event.createdByUserId() == null
                            ? Set.of() : withoutActor(List.of(event.createdByUserId()), event.actorId());
                    notificationService.notifyUsers(event.companyId(), creator, new NotificationService.Spec(
                            "TRANSFER_REJECTED", "warning", "Transferencia rechazada",
                            number + " fue rechazada.", "Transferencia", String.valueOf(event.transferId()), link));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to evaluate transfer notifications for transfer {}: {}", event.transferId(), e.getMessage(), e);
        }
    }

    private void notifyBranch(TransferEvent event, Long branchId, Set<String> permissions, String type,
                              String severity, String title, String message, String link) {
        Set<Long> recipients = withoutActor(
                recipientRepository.findUserIdsWithPermissionInBranch(event.companyId(), branchId, permissions),
                event.actorId());
        notificationService.notifyUsers(event.companyId(), recipients, new NotificationService.Spec(
                type, severity, title, message, "Transferencia", String.valueOf(event.transferId()), link));
    }

    // ---- sales ----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onSale(SaleEvent event) {
        if (event.type() != SaleEvent.Type.VOIDED) {
            return; // creating a sale / recording a payment is live data only, never a notification
        }
        try {
            Set<Long> managers = new HashSet<>(recipientRepository.findUserIdsWithRoleInBranch(
                    event.companyId(), event.branchId(), BRANCH_MANAGER_ROLES));
            // Company admins hear about a void wherever it happened (its audit entry is skipped, see above).
            managers.addAll(recipientRepository.findUserIdsWithRoleInCompany(event.companyId(), ADMIN_ROLES));
            managers = new HashSet<>(withoutActor(managers, event.actorId()));
            notificationService.notifyUsers(event.companyId(), managers, new NotificationService.Spec(
                    "SALE_VOIDED", "warning", "Venta anulada", "La venta " + event.saleNumber() + " fue anulada.",
                    "Venta", String.valueOf(event.saleId()), "/sales/documents/" + event.saleId()));
        } catch (Exception e) {
            log.warn("Failed to evaluate sale notifications for sale {}: {}", event.saleId(), e.getMessage(), e);
        }
    }

    // ---- any audited action ----

    /** Actions too frequent to be worth a notification each (every login would bury the rest). */
    // "Venta anulada" has its own, richer SALE_VOIDED notification (see onSale).
    static final Set<String> UNNOTIFIED_ACTIONS = Set.of("Inicio de sesión exitoso", "Venta anulada");

    /**
     * Every action written to the audit log is told to the company's super admins and administrators
     * (never the person who did it) — they oversee the whole company, so it is company-wide, not per branch.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onAudit(AuditRecordedEvent event) {
        if (UNNOTIFIED_ACTIONS.contains(event.action())) {
            return;
        }
        try {
            Set<Long> admins = withoutActor(
                    recipientRepository.findUserIdsWithRoleInCompany(event.companyId(), ADMIN_ROLES), event.actorId());
            if (admins.isEmpty()) {
                return;
            }
            String actor = event.actorId() == null ? "El sistema"
                    : userRepository.findById(event.actorId()).map(User::getFullName).orElse("Un usuario");
            String subject = event.entityLabel() == null || event.entityLabel().isBlank()
                    ? event.entityType() : event.entityLabel();
            notificationService.notifyUsers(event.companyId(), admins, new NotificationService.Spec(
                    "AUDIT_ACTION", "info", event.action(), actor + ": " + event.action() + " — " + subject + ".",
                    event.entityType(), event.resourceId(), linkFor(event.entityType(), event.resourceId())));
        } catch (Exception e) {
            log.warn("Failed to evaluate audit notification '{}': {}", event.action(), e.getMessage(), e);
        }
    }

    /** Entities that have a detail page, by the audit log's entity name → route prefix. */
    private static final java.util.Map<String, String> DETAIL_ROUTES = java.util.Map.ofEntries(
            java.util.Map.entry("Venta", "/sales/documents/"),
            java.util.Map.entry("Transferencia", "/inventory/transfers/"),
            java.util.Map.entry("Producto", "/inventory/products/"),
            java.util.Map.entry("Ajuste de Inventario", "/inventory/adjustments/"),
            java.util.Map.entry("Ajuste de Precio", "/sales/price-adjustments/"),
            java.util.Map.entry("Almacén", "/inventory/warehouses/"),
            java.util.Map.entry("Orden de Compra", "/purchasing/orders/"),
            java.util.Map.entry("Recepción", "/purchasing/receiving/"),
            java.util.Map.entry("Usuario", "/administration/users/"),
            java.util.Map.entry("Rol", "/administration/roles/"));
    private static final Set<String> SETTINGS_ENTITIES =
            Set.of("Categoría", "Marca", "Unidad", "Impuesto", "Sucursal", "Empresa");

    /** Where clicking the notification should land: the record's own detail page when it has one. */
    static String linkFor(String entityType, String resourceId) {
        String prefix = DETAIL_ROUTES.get(entityType);
        if (prefix != null && resourceId != null && resourceId.matches("[0-9]+")) {
            return prefix + resourceId;
        }
        return SETTINGS_ENTITIES.contains(entityType) ? "/administration/settings" : "/administration/audit-logs";
    }

    // ---- helpers ----

    private static Set<Long> withoutActor(java.util.Collection<Long> userIds, Long actorId) {
        return userIds.stream().filter(id -> !id.equals(actorId)).collect(Collectors.toSet());
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
