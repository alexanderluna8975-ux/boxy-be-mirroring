package com.boxy.boxy.modules.purchasing.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.BranchRepository;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.entity.StockLevel;
import com.boxy.boxy.modules.inventory.entity.StockMovement;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import com.boxy.boxy.modules.purchasing.dto.*;
import com.boxy.boxy.modules.purchasing.entity.*;
import com.boxy.boxy.modules.purchasing.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchasingService {

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final BranchRepository branchRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    // --- SUPPLIERS ---

    @Transactional(readOnly = true)
    public List<SupplierDto> getAllSuppliers() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return supplierRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toSupplierDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SupplierDto> getSuppliersPaged(String search, String status, Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Boolean isActive = "active".equalsIgnoreCase(status) ? Boolean.TRUE
                : "inactive".equalsIgnoreCase(status) ? Boolean.FALSE
                : null;
        String term = (search != null && !search.isBlank()) ? search.trim() : null;
        return supplierRepository.findAllFiltered(companyId, term, isActive, pageable)
                .map(this::toSupplierDto);
    }

    @Transactional(readOnly = true)
    public SupplierDto getSupplierById(Long id) {
        Supplier s = supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
        return toSupplierDto(s);
    }

    @Transactional
    public SupplierDto createSupplier(CreateSupplierRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        Supplier supplier = Supplier.builder()
                .company(company)
                .taxId(normalizeTaxId(request.getTaxId()))
                .name(request.getName().trim())
                .contactName(request.getContactName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .paymentTermsDays(request.getPaymentTermsDays())
                .isActive(true)
                .build();

        return toSupplierDto(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierDto updateSupplier(Long id, CreateSupplierRequest request) {
        Supplier s = supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));

        if (request.getName() != null) s.setName(request.getName().trim());
        if (request.getTaxId() != null) s.setTaxId(normalizeTaxId(request.getTaxId()));
        if (request.getContactName() != null) s.setContactName(request.getContactName());
        if (request.getEmail() != null) s.setEmail(request.getEmail());
        if (request.getPhone() != null) s.setPhone(request.getPhone());
        if (request.getAddress() != null) s.setAddress(request.getAddress());
        s.setPaymentTermsDays(request.getPaymentTermsDays());

        return toSupplierDto(supplierRepository.save(s));
    }

    @Transactional
    public SupplierDto deactivateSupplier(Long id) {
        Supplier s = supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
        s.setIsActive(!Boolean.TRUE.equals(s.getIsActive()));
        return toSupplierDto(supplierRepository.save(s));
    }

    @Transactional(readOnly = true)
    public boolean checkSupplierUnique(String field, String value, Long excludeId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Optional<Supplier> existing = supplierRepository.findByCompanyIdAndTaxIdAndDeletedAtIsNull(companyId, value);
        if (existing.isEmpty()) {
            return true;
        }
        return excludeId != null && existing.get().getId().equals(excludeId);
    }

    // --- PURCHASE ORDERS ---

    /** Frontend status vocabulary (`draft`, `pending-approval`, …) → the values stored on the entity. */
    private static List<String> mapFrontendStatus(String feStatus) {
        if (feStatus == null || feStatus.isBlank()) {
            return null;
        }
        return switch (feStatus.toLowerCase().replace('-', '_')) {
            case "draft" -> List.of("DRAFT", "ISSUED");
            case "pending_approval" -> List.of("SUBMITTED");
            case "approved" -> List.of("APPROVED");
            case "rejected" -> List.of("REJECTED");
            case "ordered" -> List.of("ORDERED");
            case "partially_received" -> List.of("PARTIALLY_RECEIVED");
            case "received" -> List.of("RECEIVED", "COMPLETED");
            case "cancelled" -> List.of("CANCELLED");
            default -> List.of(feStatus.toUpperCase());
        };
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderDto> getPurchaseOrders(Long branchId, String status, Long supplierId, String search,
                                                     String dateFrom, String dateTo, Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        List<String> statuses = mapFrontendStatus(status);
        boolean ignoreStatus = statuses == null;
        String searchTerm = (search == null || search.isBlank()) ? null : search.trim();
        java.time.LocalDate from = parseLocalDate(dateFrom);
        java.time.LocalDate to = parseLocalDate(dateTo);
        return purchaseOrderRepository
                .search(companyId, ignoreStatus, ignoreStatus ? List.of("_") : statuses, supplierId, branchId, searchTerm, from, to, pageable)
                .map(this::toPoDto);
    }

    /** FE sends a bare `yyyy-MM-dd` for `dateFrom` and a full ISO instant (`...T23:59:59.999Z`) for `dateTo` — both parse to the LocalDate `issueDate` compares against. */
    private static java.time.LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (Exception ignoredUnparsable) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDto getPurchaseOrderById(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
        return toPoDto(po);
    }

    @Transactional
    public PurchaseOrderDto createPurchaseOrder(CreatePurchaseOrderRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        Long branchId = request.getBranchId() != null ? request.getBranchId() : 1L;
        Branch branch = branchRepository.findByIdAndDeletedAtIsNull(branchId)
                .orElseGet(() -> branchRepository.findAll().stream().findFirst().orElseThrow());

        Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(request.getSupplierId())
                .orElseGet(() -> supplierRepository.findAll().stream().findFirst().orElseThrow());

        User user = userRepository.findByIdAndDeletedAtIsNull(SecurityUtils.getCurrentUserId())
                .orElseGet(() -> userRepository.findAll().stream().findFirst().orElseThrow());

        String orderNumber = "PO-" + System.currentTimeMillis();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        PurchaseOrder po = PurchaseOrder.builder()
                .company(company)
                .branch(branch)
                .supplier(supplier)
                .orderNumber(orderNumber)
                .issueDate(request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .notes(request.getNotes())
                .status("DRAFT")
                .createdBy(user)
                .build();

        if (request.getItems() != null) {
            for (var itemReq : request.getItems()) {
                Product product = productRepository.findByIdAndDeletedAtIsNull(itemReq.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

                BigDecimal unitCost = itemReq.getUnitCost() != null ? itemReq.getUnitCost()
                        : (product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.valueOf(25.0));
                BigDecimal qty = itemReq.getQuantity() != null ? itemReq.getQuantity() : BigDecimal.ONE;
                BigDecimal taxRate = itemReq.getTaxRate() != null ? itemReq.getTaxRate() : BigDecimal.valueOf(0.16);

                BigDecimal lineTotal = qty.multiply(unitCost);
                BigDecimal lineTax = lineTotal.multiply(taxRate);

                subtotal = subtotal.add(lineTotal);
                taxTotal = taxTotal.add(lineTax);

                PurchaseOrderItem item = PurchaseOrderItem.builder()
                        .purchaseOrder(po)
                        .product(product)
                        .quantityOrdered(qty)
                        .quantityReceived(BigDecimal.ZERO)
                        .unitCost(unitCost)
                        .taxRate(taxRate)
                        .totalCost(lineTotal.add(lineTax))
                        .build();

                po.getItems().add(item);
            }
        }

        po.setSubtotal(subtotal);
        po.setTaxAmount(taxTotal);
        po.setTotalAmount(subtotal.add(taxTotal));

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return toPoDto(saved);
    }

    @Transactional
    public PurchaseOrderDto updatePurchaseOrder(Long id, CreatePurchaseOrderRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
        if (request.getNotes() != null) po.setNotes(request.getNotes());
        if (request.getExpectedDeliveryDate() != null) po.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        return toPoDto(purchaseOrderRepository.save(po));
    }

    private static final Set<String> PO_TERMINAL_STATUSES = Set.of("RECEIVED", "COMPLETED", "CANCELLED");

    private PurchaseOrder findPoOrThrow(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
    }

    private void requireStatus(PurchaseOrder po, String action, String... allowed) {
        String current = po.getStatus() != null ? po.getStatus().toUpperCase() : "";
        for (String s : allowed) {
            if (s.equals(current)) {
                return;
            }
        }
        throw new BusinessException("INVALID_TRANSITION",
                "No se puede " + action + " una orden en estado '" + current + "'.");
    }

    @Transactional
    public PurchaseOrderDto submitPurchaseOrder(Long id) {
        PurchaseOrder po = findPoOrThrow(id);
        requireStatus(po, "enviar a aprobación", "DRAFT", "ISSUED", "REJECTED");
        po.setStatus("SUBMITTED");
        return toPoDto(purchaseOrderRepository.save(po));
    }

    @Transactional
    public PurchaseOrderDto approvePurchaseOrder(Long id) {
        PurchaseOrder po = findPoOrThrow(id);
        requireStatus(po, "aprobar", "SUBMITTED");
        po.setStatus("APPROVED");
        return toPoDto(purchaseOrderRepository.save(po));
    }

    @Transactional
    public PurchaseOrderDto rejectPurchaseOrder(Long id, String reason) {
        PurchaseOrder po = findPoOrThrow(id);
        requireStatus(po, "rechazar", "SUBMITTED");
        po.setStatus("REJECTED");
        po.setNotes((po.getNotes() != null ? po.getNotes() + " | Motivo rechazo: " : "Motivo rechazo: ") + reason);
        return toPoDto(purchaseOrderRepository.save(po));
    }

    @Transactional
    public PurchaseOrderDto markPurchaseOrderOrdered(Long id) {
        PurchaseOrder po = findPoOrThrow(id);
        requireStatus(po, "marcar como enviada", "APPROVED");
        po.setStatus("ORDERED");
        return toPoDto(purchaseOrderRepository.save(po));
    }

    @Transactional
    public PurchaseOrderDto cancelPurchaseOrder(Long id) {
        PurchaseOrder po = findPoOrThrow(id);
        String current = po.getStatus() != null ? po.getStatus().toUpperCase() : "";
        if (PO_TERMINAL_STATUSES.contains(current) || current.startsWith("PARTIALLY")) {
            throw new BusinessException("INVALID_TRANSITION",
                    "No se puede cancelar una orden en estado '" + current + "'.");
        }
        po.setStatus("CANCELLED");
        return toPoDto(purchaseOrderRepository.save(po));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderDto> getPendingPurchaseOrders() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return purchaseOrderRepository.findByCompanyId(companyId).stream()
                .filter(po -> !"RECEIVED".equalsIgnoreCase(po.getStatus()) && !"CANCELLED".equalsIgnoreCase(po.getStatus()))
                .map(this::toPoDto)
                .toList();
    }

    // --- GOODS RECEIPTS / RECEIVING ---

    @Transactional(readOnly = true)
    public List<GoodsReceiptDto> getAllGoodsReceipts() {
        return goodsReceiptRepository.findAll().stream()
                .map(this::toReceiptDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<GoodsReceiptDto> getGoodsReceiptsPaged(Pageable pageable) {
        return goodsReceiptRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toReceiptDto);
    }

    @Transactional(readOnly = true)
    public GoodsReceiptDto getGoodsReceiptById(Long id) {
        GoodsReceipt r = goodsReceiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GoodsReceipt", id));
        return toReceiptDto(r);
    }

    @Transactional
    public GoodsReceiptDto receiveGoods(CreateGoodsReceiptRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", request.getPurchaseOrderId()));

        Long whId = request.getWarehouseId() != null ? request.getWarehouseId() : 1L;
        Warehouse warehouse = warehouseRepository.findByIdAndDeletedAtIsNull(whId)
                .orElseGet(() -> warehouseRepository.findAll().stream().findFirst().orElseThrow());

        User user = userRepository.findByIdAndDeletedAtIsNull(SecurityUtils.getCurrentUserId())
                .orElseGet(() -> userRepository.findAll().stream().findFirst().orElseThrow());

        GoodsReceipt receipt = GoodsReceipt.builder()
                .purchaseOrder(po)
                .warehouse(warehouse)
                .receiptNumber("REC-" + System.currentTimeMillis())
                .supplierInvoiceNumber(request.getSupplierInvoiceNumber())
                .notes(request.getNotes())
                .receivedDate(Instant.now())
                .createdBy(user)
                .build();

        if (request.getItems() != null) {
            for (var itemReq : request.getItems()) {
                Product product = productRepository.findByIdAndDeletedAtIsNull(itemReq.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

                BigDecimal qtyReceived = itemReq.getQuantityReceived() != null ? itemReq.getQuantityReceived() : BigDecimal.ZERO;
                if (qtyReceived.compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // nothing to receive for this line
                }
                BigDecimal unitCost = itemReq.getUnitCost() != null ? itemReq.getUnitCost()
                        : (product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.valueOf(25.0));

                // Post the received quantity back onto the matching purchase-order line.
                final Long productId = product.getId();
                po.getItems().stream()
                        .filter(poi -> poi.getProduct() != null && productId.equals(poi.getProduct().getId()))
                        .findFirst()
                        .ifPresent(poi -> {
                            BigDecimal already = poi.getQuantityReceived() != null ? poi.getQuantityReceived() : BigDecimal.ZERO;
                            poi.setQuantityReceived(already.add(qtyReceived));
                        });

                // Update Stock Level
                StockLevel stock = stockLevelRepository.findForUpdate(warehouse.getId(), product.getId())
                        .orElseGet(() -> StockLevel.builder()
                                .warehouse(warehouse)
                                .product(product)
                                .quantityAvailable(BigDecimal.ZERO)
                                .quantityReserved(BigDecimal.ZERO)
                                .quantityInTransit(BigDecimal.ZERO)
                                .build());

                BigDecimal currStock = stock.getQuantityAvailable() != null ? stock.getQuantityAvailable() : BigDecimal.ZERO;
                stock.setQuantityAvailable(currStock.add(qtyReceived));
                stockLevelRepository.save(stock);

                // Record Kardex movement
                StockMovement movement = StockMovement.builder()
                        .warehouse(warehouse)
                        .product(product)
                        .movementType("PURCHASE_IN")
                        .quantity(qtyReceived)
                        .unitCost(unitCost)
                        .balanceAfter(stock.getQuantityAvailable())
                        .referenceType("PURCHASE_ORDER")
                        .referenceId(String.valueOf(po.getId()))
                        .notes("Goods received for PO " + po.getOrderNumber())
                        .createdBy(user)
                        .build();
                stockMovementRepository.save(movement);

                GoodsReceiptItem grItem = GoodsReceiptItem.builder()
                        .goodsReceipt(receipt)
                        .product(product)
                        .quantityReceived(qtyReceived)
                        .unitCost(unitCost)
                        .build();
                receipt.getItems().add(grItem);
            }
        }

        boolean fullyReceived = !po.getItems().isEmpty() && po.getItems().stream().allMatch(poi -> {
            BigDecimal ordered = poi.getQuantityOrdered() != null ? poi.getQuantityOrdered() : BigDecimal.ZERO;
            BigDecimal received = poi.getQuantityReceived() != null ? poi.getQuantityReceived() : BigDecimal.ZERO;
            return received.compareTo(ordered) >= 0;
        });
        po.setStatus(fullyReceived ? "RECEIVED" : "PARTIALLY_RECEIVED");
        purchaseOrderRepository.save(po);

        GoodsReceipt saved = goodsReceiptRepository.save(receipt);
        return toReceiptDto(saved);
    }

    /** Trim, and treat blank/absent as no RFC — so the unique key sees NULL, not "". */
    private String normalizeTaxId(String taxId) {
        if (taxId == null) {
            return null;
        }
        String trimmed = taxId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private SupplierDto toSupplierDto(Supplier s) {
        long orderCount = purchaseOrderRepository.countBySupplierId(s.getId());
        Instant lastOrderAt = purchaseOrderRepository.findLastOrderDateBySupplierId(s.getId());
        BigDecimal totalPurchased = purchaseOrderRepository.sumTotalAmountBySupplierId(s.getId());

        return SupplierDto.builder()
                .id(s.getId())
                .code("PRV-" + String.format("%04d", s.getId()))
                .taxId(s.getTaxId())
                .name(s.getName())
                .contactName(s.getContactName())
                .email(s.getEmail())
                .phone(s.getPhone())
                .address(s.getAddress())
                .paymentTermsDays(s.getPaymentTermsDays() != null ? s.getPaymentTermsDays() : 0)
                .isActive(Boolean.TRUE.equals(s.getIsActive()))
                .status(Boolean.TRUE.equals(s.getIsActive()) ? "active" : "inactive")
                .orderCount((int) orderCount)
                .lastOrderAt(lastOrderAt)
                .totalPurchased(totalPurchased != null ? totalPurchased : BigDecimal.ZERO)
                .createdAt(s.getCreatedAt())
                .build();
    }

    private PurchaseOrderDto toPoDto(PurchaseOrder po) {
        List<PurchaseOrderItemDto> itemDtos = po.getItems().stream()
                .map(i -> PurchaseOrderItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productSku(i.getProduct().getSku())
                        .sku(i.getProduct().getSku())
                        .productName(i.getProduct().getName())
                        .name(i.getProduct().getName())
                        .quantityOrdered(i.getQuantityOrdered())
                        .quantityReceived(i.getQuantityReceived())
                        .quantity(i.getQuantityOrdered())
                        .unitCost(i.getUnitCost())
                        .unitPrice(i.getUnitCost())
                        .taxRate(i.getTaxRate())
                        .totalCost(i.getTotalCost())
                        .lineTotal(i.getTotalCost())
                        .build())
                .toList();

        return PurchaseOrderDto.builder()
                .id(po.getId())
                .folio(po.getOrderNumber())
                .orderNumber(po.getOrderNumber())
                .branchId(po.getBranch() != null ? po.getBranch().getId() : 1L)
                .branchName(po.getBranch() != null ? po.getBranch().getName() : "Sucursal Central")
                .warehouseId(1L)
                .warehouseName("Almacén Central")
                .supplierId(po.getSupplier() != null ? po.getSupplier().getId() : 1L)
                .supplierName(po.getSupplier() != null ? po.getSupplier().getName() : "Proveedor General")
                .issueDate(po.getIssueDate())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .subtotal(po.getSubtotal())
                .taxAmount(po.getTaxAmount())
                .totalAmount(po.getTotalAmount())
                .total(po.getTotalAmount())
                .status(po.getStatus().toLowerCase())
                .notes(po.getNotes())
                .createdByName(po.getCreatedBy() != null ? po.getCreatedBy().getFullName() : "Admin")
                .lineCount(po.getItems().size())
                .items(itemDtos)
                .lines(itemDtos)
                .createdAt(po.getCreatedAt())
                .build();
    }

    private GoodsReceiptDto toReceiptDto(GoodsReceipt r) {
        List<GoodsReceiptDto.GoodsReceiptLineDto> lines = r.getItems().stream()
                .map(i -> GoodsReceiptDto.GoodsReceiptLineDto.builder()
                        .productId(i.getProduct().getId())
                        .sku(i.getProduct().getSku())
                        .productName(i.getProduct().getName())
                        .quantityReceived(i.getQuantityReceived())
                        .unitCost(i.getUnitCost())
                        .build())
                .toList();

        return GoodsReceiptDto.builder()
                .id(r.getId())
                .folio(r.getReceiptNumber())
                .purchaseOrderId(r.getPurchaseOrder() != null ? r.getPurchaseOrder().getId() : 1L)
                .purchaseOrderFolio(r.getPurchaseOrder() != null ? r.getPurchaseOrder().getOrderNumber() : "PO-1001")
                .supplierId(r.getPurchaseOrder() != null && r.getPurchaseOrder().getSupplier() != null ? r.getPurchaseOrder().getSupplier().getId() : 1L)
                .supplierName(r.getPurchaseOrder() != null && r.getPurchaseOrder().getSupplier() != null ? r.getPurchaseOrder().getSupplier().getName() : "Proveedor")
                .warehouseId(r.getWarehouse() != null ? r.getWarehouse().getId() : 1L)
                .warehouseName(r.getWarehouse() != null ? r.getWarehouse().getName() : "Almacén Central")
                .lineCount(r.getItems().size())
                .notes(r.getNotes())
                .receivedBy(r.getCreatedBy() != null ? r.getCreatedBy().getFullName() : "Admin")
                .receivedAt(r.getReceivedDate())
                .lines(lines)
                .build();
    }
}
