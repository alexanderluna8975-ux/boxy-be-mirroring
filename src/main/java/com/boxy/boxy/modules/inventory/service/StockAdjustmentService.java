package com.boxy.boxy.modules.inventory.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.core.sequence.DocumentSequenceService;
import com.boxy.boxy.core.sequence.DocumentType;
import com.boxy.boxy.core.web.DateFilterParser;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.dto.AdjustmentReasonDto;
import com.boxy.boxy.modules.inventory.dto.CreateStockAdjustmentRequest;
import com.boxy.boxy.modules.inventory.dto.StockAdjustmentDto;
import com.boxy.boxy.modules.inventory.entity.AdjustmentStatus;
import com.boxy.boxy.modules.inventory.entity.StockAdjustment;
import com.boxy.boxy.modules.inventory.entity.StockAdjustmentItem;
import com.boxy.boxy.modules.inventory.entity.StockLevel;
import com.boxy.boxy.modules.inventory.entity.StockMovement;
import com.boxy.boxy.modules.inventory.repository.StockAdjustmentRepository;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final DocumentSequenceService documentSequenceService;

    public static final List<AdjustmentReasonDto> REASONS = List.of(
            new AdjustmentReasonDto("1", "Conteo físico / Inventario cíclico"),
            new AdjustmentReasonDto("2", "Merma / Producto dañado"),
            new AdjustmentReasonDto("3", "Vencimiento / Caducidad"),
            new AdjustmentReasonDto("4", "Robo / Extravío"),
            new AdjustmentReasonDto("5", "Corrección administrativa")
    );

    /** {@link AdjustmentStatus} → the kebab-case vocabulary the FE's {@code AdjustmentStatus} type expects. */
    private static final Map<AdjustmentStatus, String> ADJUSTMENT_STATUS_TO_FE = Map.of(
            AdjustmentStatus.PENDING_APPROVAL, "pending-approval",
            AdjustmentStatus.APPROVED, "approved",
            AdjustmentStatus.REJECTED, "rejected",
            AdjustmentStatus.CANCELLED, "cancelled");

    /** Inverse of {@link #ADJUSTMENT_STATUS_TO_FE}, for the `filter.status` query param. */
    private static final Map<String, AdjustmentStatus> FE_TO_ADJUSTMENT_STATUS = Map.of(
            "pending-approval", AdjustmentStatus.PENDING_APPROVAL,
            "approved", AdjustmentStatus.APPROVED,
            "rejected", AdjustmentStatus.REJECTED,
            "cancelled", AdjustmentStatus.CANCELLED);

    @Transactional(readOnly = true)
    public List<AdjustmentReasonDto> getReasons() {
        return REASONS;
    }

    @Transactional(readOnly = true)
    public Page<StockAdjustmentDto> getAdjustments(String search, String status, Long warehouseId, String reasonId,
                                                    String dateFrom, String dateTo, Pageable pageable) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        AdjustmentStatus statusEnum = status != null ? FE_TO_ADJUSTMENT_STATUS.get(status) : null;
        String reasonName = reasonId != null
                ? REASONS.stream().filter(r -> r.getId().equals(reasonId)).map(AdjustmentReasonDto::getName).findFirst().orElse(null)
                : null;
        return stockAdjustmentRepository.findAllFiltered(
                        companyId, blankToNull(search), statusEnum, warehouseId, reasonName,
                        DateFilterParser.parseStart(dateFrom), DateFilterParser.parseEnd(dateTo), pageable)
                .map(this::toDto);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Transactional(readOnly = true)
    public StockAdjustmentDto getAdjustmentById(Long id) {
        return toDto(findOwnedAdjustment(id));
    }

    @Transactional
    public StockAdjustmentDto createAdjustment(CreateStockAdjustmentRequest request) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();

        Warehouse warehouse = warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(request.getWarehouseId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getWarehouseId()));

        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException("EMPTY_ADJUSTMENT", "An adjustment must include at least one line item.");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(SecurityUtils.requireCurrentUserId())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User not found"));

        String reasonName = request.getReason();
        if (reasonName == null && request.getReasonId() != null) {
            reasonName = REASONS.stream()
                    .filter(r -> r.getId().equals(request.getReasonId()))
                    .map(AdjustmentReasonDto::getName)
                    .findFirst()
                    .orElse("Ajuste de inventario");
        }
        if (reasonName == null) {
            reasonName = "Conteo físico";
        }

        Company company = warehouse.getBranch().getCompany();
        String folio = documentSequenceService.nextFolio(company.getId(), DocumentType.ADJUSTMENT);

        StockAdjustment adjustment = StockAdjustment.builder()
                .company(company)
                .warehouse(warehouse)
                .adjustmentNumber(folio)
                .reason(reasonName)
                .notes(request.getNotes())
                .status(AdjustmentStatus.PENDING_APPROVAL)
                .createdBy(user)
                .items(new ArrayList<>())
                .build();

        for (CreateStockAdjustmentRequest.AdjustmentLineRequest line : request.getLines()) {
            Product product = productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(line.getProductId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", line.getProductId()));
            if (Boolean.TRUE.equals(product.getHasVariants())) {
                throw new BusinessException("VARIANTS_NOT_SUPPORTED",
                        "Product '" + product.getSku() + "' has variants; adjusting it is not supported yet.");
            }

            BigDecimal delta = line.getQuantityDelta() != null ? line.getQuantityDelta() : BigDecimal.ZERO;

            // The physical-count snapshot at creation time is kept for the audit trail even
            // if live stock has since moved (see #approveAdjustment, which applies `delta`
            // against the *current* stock rather than replaying this snapshot).
            BigDecimal prevQty = line.getPreviousQuantity() != null
                    ? line.getPreviousQuantity()
                    : stockLevelRepository.findByWarehouseIdAndProductIdAndVariantIdIsNull(warehouse.getId(), product.getId())
                            .map(StockLevel::getQuantityAvailable)
                            .orElse(BigDecimal.ZERO);
            BigDecimal newQty = line.getCountedQuantity() != null ? line.getCountedQuantity() : prevQty.add(delta);

            StockAdjustmentItem item = StockAdjustmentItem.builder()
                    .adjustment(adjustment)
                    .product(product)
                    .previousQuantity(prevQty)
                    .newQuantity(newQty)
                    .differenceQuantity(delta)
                    .unitCost(product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO)
                    .build();

            adjustment.getItems().add(item);
        }

        return toDto(stockAdjustmentRepository.save(adjustment));
    }

    @Transactional
    public StockAdjustmentDto approveAdjustment(Long id) {
        StockAdjustment adj = findOwnedAdjustment(id);

        if (adj.getStatus() != AdjustmentStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Only PENDING_APPROVAL adjustments can be approved.");
        }

        Warehouse wh = adj.getWarehouse();

        for (StockAdjustmentItem item : adj.getItems()) {
            Product p = item.getProduct();
            BigDecimal delta = item.getDifferenceQuantity();

            StockLevel stockLevel = stockLevelRepository.getOrCreateForUpdate(wh, p);

            BigDecimal updatedAvailable = stockLevel.getQuantityAvailable().add(delta);
            if (updatedAvailable.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("NEGATIVE_STOCK",
                        "Approving this adjustment would take '" + p.getSku() + "' below zero at " + wh.getName() + ".");
            }
            stockLevel.setQuantityAvailable(updatedAvailable);
            stockLevelRepository.save(stockLevel);

            // Kardex movement — signed like every other movement type (SALE_OUT, TRANSFER_OUT):
            // negative for a decrease, positive for an increase, so SUM(quantity) always tracks
            // the running balance.
            StockMovement movement = StockMovement.builder()
                    .warehouse(wh)
                    .product(p)
                    .movementType(delta.compareTo(BigDecimal.ZERO) >= 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT")
                    .quantity(delta)
                    .unitCost(item.getUnitCost())
                    .balanceAfter(updatedAvailable)
                    .referenceType("ADJUSTMENT")
                    .referenceId(adj.getAdjustmentNumber())
                    .notes(adj.getReason() + (adj.getNotes() != null ? " - " + adj.getNotes() : ""))
                    .createdBy(adj.getCreatedBy())
                    .build();
            stockMovementRepository.save(movement);
        }

        adj.setStatus(AdjustmentStatus.APPROVED);
        adj.setApprovedBy(SecurityUtils.requireCurrentUserId());
        adj.setApprovedAt(Instant.now());
        stockAdjustmentRepository.save(adj);

        auditLogService.record("Ajuste de inventario aprobado", "Ajuste de Inventario",
                String.valueOf(adj.getId()), adj.getAdjustmentNumber(),
                AdjustmentStatus.PENDING_APPROVAL.name(), AdjustmentStatus.APPROVED.name());
        return toDto(adj);
    }

    @Transactional
    public StockAdjustmentDto rejectAdjustment(Long id) {
        StockAdjustment adj = findOwnedAdjustment(id);
        if (adj.getStatus() != AdjustmentStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Only PENDING_APPROVAL adjustments can be rejected.");
        }
        adj.setStatus(AdjustmentStatus.REJECTED);
        StockAdjustmentDto dto = toDto(stockAdjustmentRepository.save(adj));
        auditLogService.record("Ajuste de inventario rechazado", "Ajuste de Inventario",
                String.valueOf(adj.getId()), adj.getAdjustmentNumber(),
                AdjustmentStatus.PENDING_APPROVAL.name(), AdjustmentStatus.REJECTED.name());
        return dto;
    }

    /** 404s (not 403) on an adjustment belonging to another company — same treatment as "doesn't exist". */
    private StockAdjustment findOwnedAdjustment(Long id) {
        return stockAdjustmentRepository.findByIdAndCompanyId(id, SecurityUtils.requireCurrentCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("StockAdjustment", id));
    }

    public StockAdjustmentDto toDto(StockAdjustment adj) {
        List<StockAdjustmentDto.StockAdjustmentLineDto> lines = adj.getItems().stream()
                .map(i -> StockAdjustmentDto.StockAdjustmentLineDto.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .sku(i.getProduct().getSku())
                        .productName(i.getProduct().getName())
                        .quantityDelta(i.getDifferenceQuantity())
                        .previousQuantity(i.getPreviousQuantity())
                        .countedQuantity(i.getNewQuantity())
                        .unitCost(i.getUnitCost())
                        .build())
                .toList();

        String status = ADJUSTMENT_STATUS_TO_FE.getOrDefault(adj.getStatus(), "pending-approval");

        String reasonName = adj.getReason();
        String reasonId = "1";
        for (AdjustmentReasonDto r : REASONS) {
            if (r.getName().equalsIgnoreCase(reasonName)) {
                reasonId = r.getId();
                break;
            }
        }

        String approvedByName = adj.getApprovedBy() != null
                ? userRepository.findByIdAndDeletedAtIsNull(adj.getApprovedBy()).map(User::getFullName).orElse(null)
                : null;

        return StockAdjustmentDto.builder()
                .id(adj.getId())
                .folio(adj.getAdjustmentNumber())
                .adjustmentNumber(adj.getAdjustmentNumber())
                .warehouseId(adj.getWarehouse().getId())
                .warehouseName(adj.getWarehouse().getName())
                .warehouseCode(adj.getWarehouse().getCode())
                .reasonId(reasonId)
                .reasonName(reasonName)
                .reason(reasonName)
                .notes(adj.getNotes())
                .status(status)
                .requestedBy(adj.getCreatedBy() != null ? adj.getCreatedBy().getFullName() : "Admin")
                .requestedAt(adj.getCreatedAt())
                .approvedBy(approvedByName)
                .approvedAt(adj.getApprovedAt())
                .lineCount(lines.size())
                .lines(lines)
                .createdAt(adj.getCreatedAt())
                .build();
    }
}
