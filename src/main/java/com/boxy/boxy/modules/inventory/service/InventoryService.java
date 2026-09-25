package com.boxy.boxy.modules.inventory.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.InsufficientStockException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.pdf.PdfDocumentService;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.core.sequence.DocumentSequenceService;
import com.boxy.boxy.core.sequence.DocumentType;
import com.boxy.boxy.core.web.DateFilterParser;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.dto.*;
import com.boxy.boxy.modules.inventory.entity.StockLevel;
import com.boxy.boxy.modules.inventory.entity.StockMovement;
import com.boxy.boxy.modules.inventory.entity.StockTransfer;
import com.boxy.boxy.modules.inventory.entity.StockTransferItem;
import com.boxy.boxy.modules.inventory.entity.TransferStatus;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import com.boxy.boxy.modules.inventory.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryService {

    /** {@link TransferStatus} → the kebab-case vocabulary the FE's {@code TransferStatus} type expects. */
    private static final Map<TransferStatus, String> TRANSFER_STATUS_TO_FE = Map.of(
            TransferStatus.REQUESTED, "pending-approval",
            TransferStatus.APPROVED, "approved",
            TransferStatus.IN_TRANSIT, "shipped",
            TransferStatus.RECEIVED, "received",
            TransferStatus.REJECTED, "rejected",
            TransferStatus.CANCELLED, "cancelled");

    /** Inverse of {@link #TRANSFER_STATUS_TO_FE}, for the `filter.status` query param. */
    private static final Map<String, TransferStatus> FE_TO_TRANSFER_STATUS = Map.of(
            "pending-approval", TransferStatus.REQUESTED,
            "approved", TransferStatus.APPROVED,
            "shipped", TransferStatus.IN_TRANSIT,
            "received", TransferStatus.RECEIVED,
            "rejected", TransferStatus.REJECTED,
            "cancelled", TransferStatus.CANCELLED);

    /**
     * FE {@code MovementType} → every raw {@code movement_type} the backend writes for it
     * (inverse of the per-row mapping in {@link #toMovementDto}, but one-to-many: `purchase`
     * covers both a real purchase receipt and an initial-stock load, `adjustment` covers both
     * directions of a stock adjustment).
     */
    private static final Map<String, List<String>> FE_TO_MOVEMENT_TYPES = Map.of(
            "transfer-in", List.of("TRANSFER_IN"),
            "transfer-out", List.of("TRANSFER_OUT"),
            "sale", List.of("SALE_OUT"),
            "sale-void", List.of("RETURN"),
            "purchase", List.of("PURCHASE_IN", "INITIAL_STOCK"),
            "adjustment", List.of("ADJUSTMENT_IN", "ADJUSTMENT_OUT"));

    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockTransferRepository stockTransferRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;
    private final DocumentSequenceService documentSequenceService;
    private final PdfDocumentService pdfDocumentService;

    /** Bolivia has one fixed offset (UTC-4, no DST) — same zone used for every generated PDF's dates. */
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.of("America/La_Paz"));

    @Transactional(readOnly = true)
    public List<StockLevelDto> getStockLevelsByWarehouse(Long warehouseId) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Warehouse warehouse = warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(warehouseId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", warehouseId));

        Map<Long, StockLevel> stockByProductId = new HashMap<>();
        for (StockLevel s : stockLevelRepository.findByWarehouseId(warehouseId)) {
            stockByProductId.put(s.getProduct().getId(), s);
        }

        // Every active product in the company shows up here, even at zero — a
        // StockLevel row only exists once something (an import, a receipt, an
        // adjustment) actually touched that product+warehouse pair; without this,
        // a never-stocked product silently never appeared in the warehouse list.
        return productRepository.findByCompanyIdAndIsActiveTrueAndDeletedAtIsNull(companyId).stream()
                .map(product -> {
                    StockLevel stockLevel = stockByProductId.get(product.getId());
                    if (stockLevel == null) {
                        stockLevel = StockLevel.builder()
                                .warehouse(warehouse)
                                .product(product)
                                .quantityAvailable(BigDecimal.ZERO)
                                .quantityReserved(BigDecimal.ZERO)
                                .quantityInTransit(BigDecimal.ZERO)
                                .build();
                    }
                    return toStockLevelDto(stockLevel);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockLevelDto> getStockLevelsByProduct(Long productId) {
        requireOwnedProduct(productId);
        return stockLevelRepository.findByProductId(productId).stream()
                .map(this::toStockLevelDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDto> getMovementsByWarehouse(Long warehouseId, Pageable pageable) {
        requireOwnedWarehouse(warehouseId);
        return stockMovementRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId, pageable)
                .map(this::toMovementDto);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDto> getMovementsByProduct(Long productId, Pageable pageable) {
        requireOwnedProduct(productId);
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::toMovementDto);
    }

    @Transactional
    public StockTransferDto createTransfer(CreateStockTransferRequest request) {
        if (request.getSourceWarehouseId().equals(request.getDestinationWarehouseId())) {
            throw new BusinessException("INVALID_TRANSFER", "Source and destination warehouses cannot be the same.");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("EMPTY_TRANSFER", "A transfer must include at least one line item.");
        }

        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        Warehouse source = warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(request.getSourceWarehouseId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getSourceWarehouseId()));
        Warehouse destination = warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(request.getDestinationWarehouseId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getDestinationWarehouseId()));

        User user = userRepository.findByIdAndDeletedAtIsNull(SecurityUtils.requireCurrentUserId())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User not found"));

        // Correlative, not UUID/timestamp-derived: DocumentSequenceService row-locks a per-company
        // counter (SELECT ... FOR UPDATE), so concurrent requests in the same millisecond no
        // longer collide on the UNIQUE transfer_number column — they just queue for the lock.
        String transferNumber = documentSequenceService.nextFolio(companyId, DocumentType.TRANSFER);

        StockTransfer transfer = StockTransfer.builder()
                .company(company)
                .transferNumber(transferNumber)
                .sourceWarehouse(source)
                .destinationWarehouse(destination)
                .status(TransferStatus.REQUESTED)
                .notes(request.getNotes())
                .requestedBy(user)
                .build();

        for (var itemReq : request.getItems()) {
            if (itemReq.getQuantity() == null || itemReq.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("INVALID_QUANTITY", "Transfer quantity must be greater than zero.");
            }

            Product product = productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(itemReq.getProductId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));
            if (Boolean.TRUE.equals(product.getHasVariants())) {
                throw new BusinessException("VARIANTS_NOT_SUPPORTED",
                        "Product '" + product.getSku() + "' has variants; transferring it is not supported yet.");
            }

            StockTransferItem item = StockTransferItem.builder()
                    .transfer(transfer)
                    .product(product)
                    .quantityRequested(itemReq.getQuantity())
                    .quantityReceived(BigDecimal.ZERO)
                    .build();

            transfer.getItems().add(item);
        }

        StockTransfer saved = stockTransferRepository.save(transfer);
        return toTransferDto(saved);
    }

    /**
     * Corrects a single line's mistyped quantity, inline, before approval — REQUESTED only, since
     * once {@link #approveTransfer} runs, stock has already moved against the original quantity
     * and a plain field edit would silently desync from the Kardex. Once shipped, "Enviado" is
     * closed to edits — the receiving side only ever corrects "Recibido" (see
     * {@link #receiveTransfer}), which can land under or over what shipped instead.
     */
    @Transactional
    public StockTransferDto updateTransferLine(Long transferId, Long productId, UpdateTransferLineRequest request) {
        StockTransfer transfer = findOwnedTransfer(transferId);
        if (transfer.getStatus() != TransferStatus.REQUESTED) {
            throw new BusinessException("INVALID_STATUS",
                    "Only REQUESTED transfers can have their quantities edited; once approved, stock is already in transit.");
        }
        if (request == null || request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Transfer quantity must be greater than zero.");
        }

        StockTransferItem item = findOwnedTransferItem(transfer, productId);
        BigDecimal previous = item.getQuantityRequested();
        item.setQuantityRequested(request.getQuantity());

        StockTransfer saved = stockTransferRepository.save(transfer);
        auditLogService.record("Cantidad de transferencia editada", "Transferencia", String.valueOf(saved.getId()),
                saved.getTransferNumber(),
                item.getProduct().getSku() + ": " + previous,
                item.getProduct().getSku() + ": " + request.getQuantity());
        return toTransferDto(saved);
    }

    /**
     * Removes a product line before approval — REQUESTED only, same reasoning as
     * {@link #updateTransferLine}. Refuses to remove the last remaining line: an empty transfer
     * isn't a valid state to leave pending — reject the whole transfer instead.
     */
    @Transactional
    public StockTransferDto deleteTransferLine(Long transferId, Long productId) {
        StockTransfer transfer = findOwnedTransfer(transferId);
        if (transfer.getStatus() != TransferStatus.REQUESTED) {
            throw new BusinessException("INVALID_STATUS",
                    "Only REQUESTED transfers can have lines removed; once approved, stock is already in transit.");
        }
        if (transfer.getItems().size() <= 1) {
            throw new BusinessException("EMPTY_TRANSFER",
                    "A transfer must include at least one line item — reject the transfer instead of removing its last line.");
        }

        StockTransferItem item = findOwnedTransferItem(transfer, productId);
        transfer.getItems().remove(item);

        StockTransfer saved = stockTransferRepository.save(transfer);
        auditLogService.record("Producto quitado de transferencia", "Transferencia", String.valueOf(saved.getId()),
                saved.getTransferNumber(), item.getProduct().getSku(), null);
        return toTransferDto(saved);
    }

    private StockTransferItem findOwnedTransferItem(StockTransfer transfer, Long productId) {
        return transfer.getItems().stream()
                .filter(candidate -> candidate.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("StockTransferItem", productId));
    }

    /**
     * Receives a transfer, trusting each line's ACTUAL received quantity (defaulting to what
     * shipped when a line is omitted — an all-correct receive still needs no input). "Enviado" is
     * never touched here — it's whatever {@link #updateTransferLine} last left it at while
     * REQUESTED. A line that comes up short OR over what shipped requires {@code request.notes} to
     * be set: a shortfall is written as a {@code TRANSFER_LOSS} Kardex entry at the source, an
     * overage as an extra {@code TRANSFER_OUT} (more physically left the source than the system
     * had recorded) — either way the discrepancy never just vanishes untracked, same double-entry
     * principle every other movement type follows.
     */
    @Transactional
    public StockTransferDto receiveTransfer(Long transferId, ReceiveTransferRequest request) {
        StockTransfer transfer = findOwnedTransfer(transferId);

        if (transfer.getStatus() != TransferStatus.IN_TRANSIT) {
            throw new BusinessException("INVALID_STATUS", "Only IN_TRANSIT transfers can be received.");
        }

        Map<Long, BigDecimal> receivedByProduct = new HashMap<>();
        if (request != null && request.getItems() != null) {
            for (ReceiveTransferRequest.ReceiveItemRequest line : request.getItems()) {
                if (line.getProductId() != null && line.getQuantityReceived() != null) {
                    receivedByProduct.put(line.getProductId(), line.getQuantityReceived());
                }
            }
        }
        String notes = request != null ? request.getNotes() : null;

        // Pass 1 — validate every line before touching any stock, so a bad line (negative, or a
        // missing note on a discrepancy) fails the whole request cleanly instead of leaving a
        // partially-applied receive for @Transactional to unwind.
        boolean hasDiscrepancy = false;
        for (StockTransferItem item : transfer.getItems()) {
            BigDecimal sent = item.getQuantityRequested();
            BigDecimal received = receivedByProduct.getOrDefault(item.getProduct().getId(), sent);

            if (received.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("INVALID_QUANTITY",
                        "Received quantity for '" + item.getProduct().getSku() + "' cannot be negative.");
            }
            if (received.compareTo(sent) != 0) {
                hasDiscrepancy = true;
            }
        }
        if (hasDiscrepancy && (notes == null || notes.isBlank())) {
            throw new BusinessException("RECEIVING_NOTE_REQUIRED",
                    "A note is required when the received quantity differs from what was shipped.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Pass 2 — apply.
        for (StockTransferItem item : transfer.getItems()) {
            BigDecimal sent = item.getQuantityRequested();
            BigDecimal received = receivedByProduct.getOrDefault(item.getProduct().getId(), sent);

            // Deduct in-transit at source for the FULL shipped amount — same row the approve step
            // locked and incremented, so it must already exist; a missing row here means the two
            // have drifted apart. It's no longer "in transit" either way, whether it arrived, was
            // lost, or turned out to be more than what was recorded as shipped.
            StockLevel sourceStock = stockLevelRepository.findForUpdate(transfer.getSourceWarehouse().getId(), item.getProduct().getId())
                    .orElseThrow(() -> new BusinessException("STOCK_INCONSISTENT",
                            "No stock record at the source warehouse for product '" + item.getProduct().getSku() + "'."));
            sourceStock.setQuantityInTransit(sourceStock.getQuantityInTransit().subtract(sent));

            // An overage means more physically left the source than the system recorded as shipped
            // — that extra amount was never deducted from "available" at dispatch time, so it has
            // to come out now, discovered only at receiving.
            BigDecimal overage = received.subtract(sent).max(BigDecimal.ZERO);
            if (overage.compareTo(BigDecimal.ZERO) > 0) {
                sourceStock.setQuantityAvailable(sourceStock.getQuantityAvailable().subtract(overage));
            }
            stockLevelRepository.save(sourceStock);

            // Credit destination with only what actually arrived.
            StockLevel destStock = stockLevelRepository.getOrCreateForUpdate(transfer.getDestinationWarehouse(), item.getProduct());
            destStock.setQuantityAvailable(destStock.getQuantityAvailable().add(received));
            stockLevelRepository.save(destStock);

            item.setQuantityReceived(received);

            StockMovement inMovement = StockMovement.builder()
                    .warehouse(transfer.getDestinationWarehouse())
                    .product(item.getProduct())
                    .movementType("TRANSFER_IN")
                    .quantity(received)
                    .unitCost(item.getProduct().getCostPrice())
                    .balanceAfter(destStock.getQuantityAvailable())
                    .referenceType("TRANSFER")
                    .referenceId(String.valueOf(transfer.getId()))
                    .notes("Received transfer from " + transfer.getSourceWarehouse().getName())
                    .createdBy(transfer.getRequestedBy())
                    .build();
            stockMovementRepository.save(inMovement);

            BigDecimal shortage = sent.subtract(received).max(BigDecimal.ZERO);
            if (shortage.compareTo(BigDecimal.ZERO) > 0) {
                StockMovement lossMovement = StockMovement.builder()
                        .warehouse(transfer.getSourceWarehouse())
                        .product(item.getProduct())
                        .movementType("TRANSFER_LOSS")
                        .quantity(shortage.negate())
                        .unitCost(item.getProduct().getCostPrice())
                        .balanceAfter(sourceStock.getQuantityAvailable())
                        .referenceType("TRANSFER")
                        .referenceId(String.valueOf(transfer.getId()))
                        .notes(notes)
                        .createdBy(transfer.getRequestedBy())
                        .build();
                stockMovementRepository.save(lossMovement);
            } else if (overage.compareTo(BigDecimal.ZERO) > 0) {
                StockMovement overageMovement = StockMovement.builder()
                        .warehouse(transfer.getSourceWarehouse())
                        .product(item.getProduct())
                        .movementType("TRANSFER_OUT")
                        .quantity(overage.negate())
                        .unitCost(item.getProduct().getCostPrice())
                        .balanceAfter(sourceStock.getQuantityAvailable())
                        .referenceType("TRANSFER")
                        .referenceId(String.valueOf(transfer.getId()))
                        .notes(notes)
                        .createdBy(transfer.getRequestedBy())
                        .build();
                stockMovementRepository.save(overageMovement);
            }
        }

        transfer.setStatus(TransferStatus.RECEIVED);
        transfer.setReceivedBy(SecurityUtils.requireCurrentUserId());
        transfer.setReceivedAt(Instant.now());
        if (notes != null && !notes.isBlank()) {
            transfer.setReceivingNotes(notes);
        }

        StockTransfer saved = stockTransferRepository.save(transfer);
        auditLogService.record("Transferencia recibida", "Transferencia", String.valueOf(saved.getId()),
                saved.getTransferNumber(), TransferStatus.IN_TRANSIT.name(), TransferStatus.RECEIVED.name());
        return toTransferDto(saved);
    }

    private StockLevelDto toStockLevelDto(StockLevel s) {
        BigDecimal available = s.getQuantityAvailable() != null ? s.getQuantityAvailable() : BigDecimal.ZERO;
        BigDecimal reserved = s.getQuantityReserved() != null ? s.getQuantityReserved() : BigDecimal.ZERO;
        BigDecimal totalQty = available.add(reserved);
        boolean isLow = available.compareTo(s.getProduct().getMinStockAlert()) <= 0 && available.compareTo(BigDecimal.ZERO) > 0;
        String status = available.compareTo(BigDecimal.ZERO) <= 0 ? "out-of-stock" : (isLow ? "low-stock" : "in-stock");

        return StockLevelDto.builder()
                .id(s.getId())
                .warehouseId(s.getWarehouse().getId())
                .warehouseName(s.getWarehouse().getName())
                .warehouseCode(s.getWarehouse().getCode())
                .branchId(s.getWarehouse().getBranch().getId())
                .branchName(s.getWarehouse().getBranch().getName())
                .productId(s.getProduct().getId())
                .productSku(s.getProduct().getSku())
                .sku(s.getProduct().getSku())
                .productName(s.getProduct().getName())
                .quantityAvailable(available)
                .quantityReserved(reserved)
                .quantityInTransit(s.getQuantityInTransit())
                .quantity(totalQty)
                .reservedQuantity(reserved)
                .availableQuantity(available)
                .minStockAlert(s.getProduct().getMinStockAlert())
                .isLowStock(isLow)
                .status(status)
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDto> getAllMovements(Long warehouseId, Long productId, String type,
                                                   String dateFrom, String dateTo, String search, Pageable pageable) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        List<String> movementTypes = type != null ? FE_TO_MOVEMENT_TYPES.get(type) : null;
        return stockMovementRepository.findAllFiltered(
                        companyId, warehouseId, productId, movementTypes,
                        DateFilterParser.parseStart(dateFrom), DateFilterParser.parseEnd(dateTo),
                        blankToNull(search), pageable)
                .map(this::toMovementDto);
    }

    @Transactional(readOnly = true)
    public Page<StockTransferDto> getAllTransfers(String search, String status, Long sourceWarehouseId,
                                                   Long destinationWarehouseId, String dateFrom, String dateTo,
                                                   Pageable pageable) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        TransferStatus statusEnum = status != null ? FE_TO_TRANSFER_STATUS.get(status) : null;
        return stockTransferRepository.findAllFiltered(
                        companyId, blankToNull(search), statusEnum, sourceWarehouseId, destinationWarehouseId,
                        DateFilterParser.parseStart(dateFrom), DateFilterParser.parseEnd(dateTo), pageable)
                .map(this::toTransferDto);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Transactional(readOnly = true)
    public StockTransferDto getTransferById(Long id) {
        return toTransferDto(findOwnedTransfer(id));
    }

    /** Only meaningful once shipped — before that there's nothing to hand the driver. */
    @Transactional(readOnly = true)
    public byte[] generateTransferPdf(Long id) {
        StockTransfer transfer = findOwnedTransfer(id);

        List<Map<String, Object>> lines = transfer.getItems().stream()
                .map(item -> {
                    Map<String, Object> line = new HashMap<>();
                    line.put("sku", item.getProduct().getSku());
                    line.put("productName", item.getProduct().getName());
                    line.put("quantitySent", item.getQuantityRequested());
                    line.put("quantityReceived", transfer.getStatus() == TransferStatus.RECEIVED ? item.getQuantityReceived() : null);
                    return line;
                })
                .toList();

        Map<String, Object> partyFields = new LinkedHashMap<>();
        partyFields.put("Almacén Destino", transfer.getDestinationWarehouse().getName());

        Map<String, Object> metaFields = new LinkedHashMap<>();
        metaFields.put("Folio", transfer.getTransferNumber());
        metaFields.put("Almacén Origen", transfer.getSourceWarehouse().getName());
        if (transfer.getDispatchedAt() != null) {
            metaFields.put("Fecha de Envío", PDF_DATE.format(transfer.getDispatchedAt()));
        }

        Map<String, Object> model = new HashMap<>();
        model.put("company", transfer.getCompany());
        model.put("folio", transfer.getTransferNumber());
        model.put("partyFields", partyFields);
        model.put("metaFields", metaFields);
        model.put("notes", transfer.getNotes() != null ? transfer.getNotes() : "");
        model.put("requestedByName", transfer.getRequestedBy() != null ? transfer.getRequestedBy().getFullName() : "");
        model.put("receivedByName", transfer.getStatus() == TransferStatus.RECEIVED
                ? userRepository.findByIdAndDeletedAtIsNull(transfer.getReceivedBy()).map(User::getFullName).orElse("")
                : "");
        model.put("lines", lines);

        return pdfDocumentService.render("transfer-note", model);
    }

    /**
     * Approving now IS dispatching — one action, not two. Moves stock out of the source
     * immediately (same validation/Kardex logic the old, separate {@code dispatchTransfer} used
     * to run) and lands straight on {@code IN_TRANSIT}; {@code APPROVED} is still recorded via
     * {@code approvedBy}/{@code approvedAt} for the audit trail, it's just never the persisted
     * {@code status} on its own anymore.
     */
    @Transactional
    public StockTransferDto approveTransfer(Long id) {
        StockTransfer t = findOwnedTransfer(id);
        if (t.getStatus() != TransferStatus.REQUESTED) {
            throw new BusinessException("INVALID_STATUS", "Only REQUESTED transfers can be approved.");
        }

        for (StockTransferItem item : t.getItems()) {
            StockLevel stock = stockLevelRepository.findForUpdate(t.getSourceWarehouse().getId(), item.getProduct().getId())
                    .orElseThrow(() -> new InsufficientStockException(item.getProduct().getSku(), item.getProduct().getName(), 0, item.getQuantityRequested().doubleValue()));

            if (stock.getQuantityAvailable().compareTo(item.getQuantityRequested()) < 0) {
                throw new InsufficientStockException(item.getProduct().getSku(), item.getProduct().getName(),
                        stock.getQuantityAvailable().doubleValue(), item.getQuantityRequested().doubleValue());
            }

            stock.setQuantityAvailable(stock.getQuantityAvailable().subtract(item.getQuantityRequested()));
            stock.setQuantityInTransit(stock.getQuantityInTransit().add(item.getQuantityRequested()));
            stockLevelRepository.save(stock);

            StockMovement movement = StockMovement.builder()
                    .warehouse(t.getSourceWarehouse())
                    .product(item.getProduct())
                    .movementType("TRANSFER_OUT")
                    .quantity(item.getQuantityRequested().negate())
                    .unitCost(item.getProduct().getCostPrice())
                    .balanceAfter(stock.getQuantityAvailable())
                    .referenceType("TRANSFER")
                    .referenceId(String.valueOf(t.getId()))
                    .notes("Dispatched transfer to " + t.getDestinationWarehouse().getName())
                    .createdBy(t.getRequestedBy())
                    .build();
            stockMovementRepository.save(movement);
        }

        Long actorId = SecurityUtils.requireCurrentUserId();
        Instant now = Instant.now();
        t.setStatus(TransferStatus.IN_TRANSIT);
        t.setApprovedBy(actorId);
        t.setApprovedAt(now);
        t.setDispatchedBy(actorId);
        t.setDispatchedAt(now);

        StockTransfer saved = stockTransferRepository.save(t);
        auditLogService.record("Transferencia aprobada y enviada", "Transferencia", String.valueOf(saved.getId()),
                saved.getTransferNumber(), TransferStatus.REQUESTED.name(), TransferStatus.IN_TRANSIT.name());
        return toTransferDto(saved);
    }

    @Transactional
    public StockTransferDto rejectTransfer(Long id) {
        StockTransfer t = findOwnedTransfer(id);
        if (t.getStatus() != TransferStatus.REQUESTED) {
            throw new BusinessException("INVALID_STATUS",
                    "Only REQUESTED transfers can be rejected; once approved, stock is already in transit.");
        }
        TransferStatus previousStatus = t.getStatus();
        t.setStatus(TransferStatus.REJECTED);
        StockTransfer saved = stockTransferRepository.save(t);
        auditLogService.record("Transferencia rechazada", "Transferencia", String.valueOf(saved.getId()),
                saved.getTransferNumber(), previousStatus.name(), TransferStatus.REJECTED.name());
        return toTransferDto(saved);
    }

    /** 404s (not 403) on a transfer belonging to another company — same treatment as "doesn't exist". */
    private StockTransfer findOwnedTransfer(Long id) {
        return stockTransferRepository.findByIdAndCompanyId(id, SecurityUtils.requireCurrentCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", id));
    }

    private void requireOwnedWarehouse(Long warehouseId) {
        warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(warehouseId, SecurityUtils.requireCurrentCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", warehouseId));
    }

    private void requireOwnedProduct(Long productId) {
        productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(productId, SecurityUtils.requireCurrentCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    /** Every {@code movement_type} the backend actually writes → the FE's closed {@code MovementType} union. */
    private static final Map<String, String> MOVEMENT_TYPE_TO_FE = Map.of(
            "TRANSFER_IN", "transfer-in",
            "TRANSFER_OUT", "transfer-out",
            "SALE_OUT", "sale",
            "PURCHASE_IN", "purchase",
            "INITIAL_STOCK", "purchase",
            "ADJUSTMENT_IN", "adjustment",
            "ADJUSTMENT_OUT", "adjustment");

    private StockMovementDto toMovementDto(StockMovement m) {
        String type = MOVEMENT_TYPE_TO_FE.getOrDefault(m.getMovementType(), "adjustment");

        return StockMovementDto.builder()
                .id(m.getId())
                .warehouseId(m.getWarehouse().getId())
                .warehouseName(m.getWarehouse().getName())
                .warehouseCode(m.getWarehouse().getCode())
                .productId(m.getProduct().getId())
                .productSku(m.getProduct().getSku())
                .sku(m.getProduct().getSku())
                .productName(m.getProduct().getName())
                .movementType(m.getMovementType())
                .type(type)
                .quantity(m.getQuantity())
                .unitCost(m.getUnitCost())
                .balanceAfter(m.getBalanceAfter())
                .referenceType(m.getReferenceType())
                .referenceId(m.getReferenceId())
                .referenceFolio(m.getReferenceId() != null ? m.getReferenceId() : "REF-" + m.getId())
                .notes(m.getNotes())
                .createdByName(m.getCreatedBy() != null ? m.getCreatedBy().getFullName() : "SYSTEM")
                .userId(m.getCreatedBy() != null ? String.valueOf(m.getCreatedBy().getId()) : "1")
                .createdAt(m.getCreatedAt())
                .occurredAt(m.getCreatedAt())
                .build();
    }

    private StockTransferDto toTransferDto(StockTransfer t) {
        List<StockTransferItemDto> itemDtos = t.getItems().stream()
                .map(i -> StockTransferItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productSku(i.getProduct().getSku())
                        .sku(i.getProduct().getSku())
                        .productName(i.getProduct().getName())
                        .unitName(i.getProduct().getUnit() != null ? i.getProduct().getUnit().getName() : "")
                        .quantityRequested(i.getQuantityRequested())
                        .quantityReceived(i.getQuantityReceived())
                        .quantity(i.getQuantityRequested())
                        .build())
                .toList();

        String status = TRANSFER_STATUS_TO_FE.getOrDefault(t.getStatus(), "pending-approval");
        String approvedByName = t.getApprovedBy() != null
                ? userRepository.findByIdAndDeletedAtIsNull(t.getApprovedBy()).map(User::getFullName).orElse(null)
                : null;

        return StockTransferDto.builder()
                .id(t.getId())
                .transferNumber(t.getTransferNumber())
                .folio(t.getTransferNumber())
                .sourceWarehouseId(t.getSourceWarehouse().getId())
                .originWarehouseId(t.getSourceWarehouse().getId())
                .sourceWarehouseName(t.getSourceWarehouse().getName())
                .originWarehouseName(t.getSourceWarehouse().getName())
                .sourceWarehouseCode(t.getSourceWarehouse().getCode())
                .originWarehouseCode(t.getSourceWarehouse().getCode())
                .destinationWarehouseId(t.getDestinationWarehouse().getId())
                .destinationWarehouseName(t.getDestinationWarehouse().getName())
                .destinationWarehouseCode(t.getDestinationWarehouse().getCode())
                .status(status)
                .notes(t.getNotes())
                .receivingNotes(t.getReceivingNotes())
                .requestedByName(t.getRequestedBy() != null ? t.getRequestedBy().getFullName() : "Admin")
                .requestedBy(t.getRequestedBy() != null ? t.getRequestedBy().getFullName() : "Admin")
                .requestedAt(t.getCreatedAt())
                .approvedBy(approvedByName)
                .approvedAt(t.getApprovedAt())
                .dispatchedAt(t.getDispatchedAt())
                .shippedAt(t.getDispatchedAt())
                .receivedAt(t.getReceivedAt())
                .items(itemDtos)
                .lines(itemDtos)
                .lineCount(itemDtos.size())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
