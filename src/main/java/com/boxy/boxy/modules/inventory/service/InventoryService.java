package com.boxy.boxy.modules.inventory.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.InsufficientStockException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.entity.Company;
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
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import com.boxy.boxy.modules.inventory.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockTransferRepository stockTransferRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public List<StockLevelDto> getStockLevelsByWarehouse(Long warehouseId) {
        return stockLevelRepository.findByWarehouseId(warehouseId).stream()
                .map(this::toStockLevelDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDto> getMovementsByWarehouse(Long warehouseId, Pageable pageable) {
        return stockMovementRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId, pageable)
                .map(this::toMovementDto);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementDto> getMovementsByProduct(Long productId, Pageable pageable) {
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::toMovementDto);
    }

    @Transactional
    public StockTransferDto createTransfer(CreateStockTransferRequest request) {
        if (request.getSourceWarehouseId().equals(request.getDestinationWarehouseId())) {
            throw new BusinessException("INVALID_TRANSFER", "Source and destination warehouses cannot be the same.");
        }

        Warehouse source = warehouseRepository.findByIdAndDeletedAtIsNull(request.getSourceWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getSourceWarehouseId()));
        Warehouse destination = warehouseRepository.findByIdAndDeletedAtIsNull(request.getDestinationWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getDestinationWarehouseId()));

        User user = userRepository.findByIdAndDeletedAtIsNull(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User not found"));

        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        String transferNumber = "TRF-" + System.currentTimeMillis();

        StockTransfer transfer = StockTransfer.builder()
                .company(company)
                .transferNumber(transferNumber)
                .sourceWarehouse(source)
                .destinationWarehouse(destination)
                .status("REQUESTED")
                .notes(request.getNotes())
                .requestedBy(user)
                .build();

        for (var itemReq : request.getItems()) {
            Product product = productRepository.findByIdAndDeletedAtIsNull(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

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

    @Transactional
    public StockTransferDto dispatchTransfer(Long transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", transferId));

        if (!"REQUESTED".equals(transfer.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Only REQUESTED transfers can be dispatched.");
        }

        for (StockTransferItem item : transfer.getItems()) {
            StockLevel stock = stockLevelRepository.findForUpdate(transfer.getSourceWarehouse().getId(), item.getProduct().getId())
                    .orElseThrow(() -> new InsufficientStockException(item.getProduct().getSku(), item.getProduct().getName(), 0, item.getQuantityRequested().doubleValue()));

            if (stock.getQuantityAvailable().compareTo(item.getQuantityRequested()) < 0) {
                throw new InsufficientStockException(item.getProduct().getSku(), item.getProduct().getName(),
                        stock.getQuantityAvailable().doubleValue(), item.getQuantityRequested().doubleValue());
            }

            stock.setQuantityAvailable(stock.getQuantityAvailable().subtract(item.getQuantityRequested()));
            stock.setQuantityInTransit(stock.getQuantityInTransit().add(item.getQuantityRequested()));
            stockLevelRepository.save(stock);

            // Record Kardex movement
            StockMovement movement = StockMovement.builder()
                    .warehouse(transfer.getSourceWarehouse())
                    .product(item.getProduct())
                    .movementType("TRANSFER_OUT")
                    .quantity(item.getQuantityRequested().negate())
                    .unitCost(item.getProduct().getCostPrice())
                    .balanceAfter(stock.getQuantityAvailable())
                    .referenceType("TRANSFER")
                    .referenceId(String.valueOf(transfer.getId()))
                    .notes("Dispatched transfer to " + transfer.getDestinationWarehouse().getName())
                    .createdBy(transfer.getRequestedBy())
                    .build();
            stockMovementRepository.save(movement);
        }

        transfer.setStatus("IN_TRANSIT");
        transfer.setDispatchedBy(SecurityUtils.getCurrentUserId());
        transfer.setDispatchedAt(Instant.now());

        return toTransferDto(stockTransferRepository.save(transfer));
    }

    @Transactional
    public StockTransferDto receiveTransfer(Long transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", transferId));

        if (!"IN_TRANSIT".equals(transfer.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Only IN_TRANSIT transfers can be received.");
        }

        for (StockTransferItem item : transfer.getItems()) {
            // Deduct in-transit at source
            StockLevel sourceStock = stockLevelRepository.findByWarehouseIdAndProductIdAndVariantIdIsNull(
                    transfer.getSourceWarehouse().getId(), item.getProduct().getId())
                    .orElse(null);
            if (sourceStock != null) {
                sourceStock.setQuantityInTransit(sourceStock.getQuantityInTransit().subtract(item.getQuantityRequested()));
                stockLevelRepository.save(sourceStock);
            }

            // Increase available at destination
            StockLevel destStock = stockLevelRepository.findForUpdate(transfer.getDestinationWarehouse().getId(), item.getProduct().getId())
                    .orElseGet(() -> StockLevel.builder()
                            .warehouse(transfer.getDestinationWarehouse())
                            .product(item.getProduct())
                            .quantityAvailable(BigDecimal.ZERO)
                            .quantityReserved(BigDecimal.ZERO)
                            .quantityInTransit(BigDecimal.ZERO)
                            .build());

            destStock.setQuantityAvailable(destStock.getQuantityAvailable().add(item.getQuantityRequested()));
            stockLevelRepository.save(destStock);

            item.setQuantityReceived(item.getQuantityRequested());

            // Record Kardex movement
            StockMovement movement = StockMovement.builder()
                    .warehouse(transfer.getDestinationWarehouse())
                    .product(item.getProduct())
                    .movementType("TRANSFER_IN")
                    .quantity(item.getQuantityRequested())
                    .unitCost(item.getProduct().getCostPrice())
                    .balanceAfter(destStock.getQuantityAvailable())
                    .referenceType("TRANSFER")
                    .referenceId(String.valueOf(transfer.getId()))
                    .notes("Received transfer from " + transfer.getSourceWarehouse().getName())
                    .createdBy(transfer.getRequestedBy())
                    .build();
            stockMovementRepository.save(movement);
        }

        transfer.setStatus("RECEIVED");
        transfer.setReceivedBy(SecurityUtils.getCurrentUserId());
        transfer.setReceivedAt(Instant.now());

        return toTransferDto(stockTransferRepository.save(transfer));
    }

    private StockLevelDto toStockLevelDto(StockLevel s) {
        boolean isLow = s.getQuantityAvailable().compareTo(s.getProduct().getMinStockAlert()) <= 0;
        return StockLevelDto.builder()
                .id(s.getId())
                .warehouseId(s.getWarehouse().getId())
                .warehouseName(s.getWarehouse().getName())
                .branchId(s.getWarehouse().getBranch().getId())
                .branchName(s.getWarehouse().getBranch().getName())
                .productId(s.getProduct().getId())
                .productSku(s.getProduct().getSku())
                .productName(s.getProduct().getName())
                .quantityAvailable(s.getQuantityAvailable())
                .quantityReserved(s.getQuantityReserved())
                .quantityInTransit(s.getQuantityInTransit())
                .minStockAlert(s.getProduct().getMinStockAlert())
                .isLowStock(isLow)
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private StockMovementDto toMovementDto(StockMovement m) {
        return StockMovementDto.builder()
                .id(m.getId())
                .warehouseId(m.getWarehouse().getId())
                .warehouseName(m.getWarehouse().getName())
                .productId(m.getProduct().getId())
                .productSku(m.getProduct().getSku())
                .productName(m.getProduct().getName())
                .movementType(m.getMovementType())
                .quantity(m.getQuantity())
                .unitCost(m.getUnitCost())
                .balanceAfter(m.getBalanceAfter())
                .referenceType(m.getReferenceType())
                .referenceId(m.getReferenceId())
                .notes(m.getNotes())
                .createdByName(m.getCreatedBy() != null ? m.getCreatedBy().getFullName() : "SYSTEM")
                .createdAt(m.getCreatedAt())
                .build();
    }

    private StockTransferDto toTransferDto(StockTransfer t) {
        List<StockTransferItemDto> itemDtos = t.getItems().stream()
                .map(i -> StockTransferItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productSku(i.getProduct().getSku())
                        .productName(i.getProduct().getName())
                        .quantityRequested(i.getQuantityRequested())
                        .quantityReceived(i.getQuantityReceived())
                        .build())
                .toList();

        return StockTransferDto.builder()
                .id(t.getId())
                .transferNumber(t.getTransferNumber())
                .sourceWarehouseId(t.getSourceWarehouse().getId())
                .sourceWarehouseName(t.getSourceWarehouse().getName())
                .destinationWarehouseId(t.getDestinationWarehouse().getId())
                .destinationWarehouseName(t.getDestinationWarehouse().getName())
                .status(t.getStatus())
                .notes(t.getNotes())
                .requestedByName(t.getRequestedBy().getFullName())
                .dispatchedAt(t.getDispatchedAt())
                .receivedAt(t.getReceivedAt())
                .items(itemDtos)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
