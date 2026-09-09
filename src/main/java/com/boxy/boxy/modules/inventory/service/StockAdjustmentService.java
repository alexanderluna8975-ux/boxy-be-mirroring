package com.boxy.boxy.modules.inventory.service;

import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.dto.AdjustmentReasonDto;
import com.boxy.boxy.modules.inventory.dto.CreateStockAdjustmentRequest;
import com.boxy.boxy.modules.inventory.dto.StockAdjustmentDto;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public static final List<AdjustmentReasonDto> REASONS = List.of(
            new AdjustmentReasonDto("1", "Conteo físico / Inventario cíclico"),
            new AdjustmentReasonDto("2", "Merma / Producto dañado"),
            new AdjustmentReasonDto("3", "Vencimiento / Caducidad"),
            new AdjustmentReasonDto("4", "Robo / Extravío"),
            new AdjustmentReasonDto("5", "Corrección administrativa")
    );

    @Transactional(readOnly = true)
    public List<AdjustmentReasonDto> getReasons() {
        return REASONS;
    }

    @Transactional(readOnly = true)
    public Page<StockAdjustmentDto> getAdjustments(Pageable pageable) {
        return stockAdjustmentRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public StockAdjustmentDto getAdjustmentById(Long id) {
        StockAdjustment adj = stockAdjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockAdjustment", id));
        return toDto(adj);
    }

    @Transactional
    public StockAdjustmentDto createAdjustment(CreateStockAdjustmentRequest request) {
        Warehouse warehouse = warehouseRepository.findByIdAndDeletedAtIsNull(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getWarehouseId()));

        Long currentUserId = SecurityUtils.getCurrentUserId();
        User user = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;
        if (user == null) {
            user = userRepository.findAll().stream().findFirst().orElse(null);
        }

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

        String folio = "ADJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        StockAdjustment adjustment = StockAdjustment.builder()
                .company(warehouse.getBranch().getCompany())
                .warehouse(warehouse)
                .adjustmentNumber(folio)
                .reason(reasonName)
                .notes(request.getNotes())
                .status("pending-approval")
                .createdBy(user)
                .items(new ArrayList<>())
                .build();

        if (request.getLines() != null) {
            for (CreateStockAdjustmentRequest.AdjustmentLineRequest line : request.getLines()) {
                Product product = productRepository.findByIdAndDeletedAtIsNull(line.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", line.getProductId()));

                BigDecimal prevQty = stockLevelRepository.findByWarehouseIdAndProductIdAndVariantIdIsNull(warehouse.getId(), product.getId())
                        .map(StockLevel::getQuantityAvailable)
                        .orElse(BigDecimal.ZERO);

                BigDecimal delta = line.getQuantityDelta() != null ? line.getQuantityDelta() : BigDecimal.ZERO;
                BigDecimal newQty = prevQty.add(delta);

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
        }

        return toDto(stockAdjustmentRepository.save(adjustment));
    }

    @Transactional
    public StockAdjustmentDto approveAdjustment(Long id) {
        StockAdjustment adj = stockAdjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockAdjustment", id));

        if (!"approved".equalsIgnoreCase(adj.getStatus())) {
            Warehouse wh = adj.getWarehouse();

            for (StockAdjustmentItem item : adj.getItems()) {
                Product p = item.getProduct();
                BigDecimal delta = item.getDifferenceQuantity();

                StockLevel stockLevel = stockLevelRepository.findForUpdate(wh.getId(), p.getId())
                        .orElseGet(() -> StockLevel.builder()
                                .warehouse(wh)
                                .product(p)
                                .quantityAvailable(BigDecimal.ZERO)
                                .quantityReserved(BigDecimal.ZERO)
                                .quantityInTransit(BigDecimal.ZERO)
                                .build());

                BigDecimal updatedAvailable = stockLevel.getQuantityAvailable().add(delta);
                if (updatedAvailable.compareTo(BigDecimal.ZERO) < 0) {
                    updatedAvailable = BigDecimal.ZERO;
                }
                stockLevel.setQuantityAvailable(updatedAvailable);
                stockLevelRepository.save(stockLevel);

                // Kardex movement
                StockMovement movement = StockMovement.builder()
                        .warehouse(wh)
                        .product(p)
                        .movementType(delta.compareTo(BigDecimal.ZERO) >= 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT")
                        .quantity(delta.abs())
                        .unitCost(item.getUnitCost())
                        .balanceAfter(updatedAvailable)
                        .referenceType("ADJUSTMENT")
                        .referenceId(adj.getAdjustmentNumber())
                        .notes(adj.getReason() + (adj.getNotes() != null ? " - " + adj.getNotes() : ""))
                        .createdBy(adj.getCreatedBy())
                        .build();
                stockMovementRepository.save(movement);
            }

            adj.setStatus("approved");
            stockAdjustmentRepository.save(adj);
        }

        return toDto(adj);
    }

    @Transactional
    public StockAdjustmentDto rejectAdjustment(Long id) {
        StockAdjustment adj = stockAdjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockAdjustment", id));
        adj.setStatus("rejected");
        return toDto(stockAdjustmentRepository.save(adj));
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
                        .newQuantity(i.getNewQuantity())
                        .unitCost(i.getUnitCost())
                        .build())
                .toList();

        String status = adj.getStatus() != null ? adj.getStatus().toLowerCase() : "pending-approval";
        if ("completed".equalsIgnoreCase(status)) status = "approved";

        String reasonName = adj.getReason();
        String reasonId = "1";
        for (AdjustmentReasonDto r : REASONS) {
            if (r.getName().equalsIgnoreCase(reasonName)) {
                reasonId = r.getId();
                break;
            }
        }

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
                .approvedBy(status.equals("approved") ? "Admin" : null)
                .approvedAt(status.equals("approved") ? adj.getCreatedAt() : null)
                .lineCount(lines.size())
                .lines(lines)
                .createdAt(adj.getCreatedAt())
                .build();
    }
}
