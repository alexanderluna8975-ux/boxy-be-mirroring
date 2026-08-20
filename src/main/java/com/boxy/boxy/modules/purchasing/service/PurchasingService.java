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
import java.util.List;

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

    @Transactional(readOnly = true)
    public List<SupplierDto> getAllSuppliers() {
        String companyId = SecurityUtils.getCurrentCompanyId();
        return supplierRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toSupplierDto)
                .toList();
    }

    @Transactional
    public SupplierDto createSupplier(CreateSupplierRequest request) {
        String companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        Supplier supplier = Supplier.builder()
                .company(company)
                .taxId(request.getTaxId().trim())
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

    @Transactional(readOnly = true)
    public Page<PurchaseOrderDto> getPurchaseOrders(String branchId, Pageable pageable) {
        if (branchId != null) {
            return purchaseOrderRepository.findByBranchIdOrderByCreatedAtDesc(branchId, pageable).map(this::toPoDto);
        }
        String companyId = SecurityUtils.getCurrentCompanyId();
        return purchaseOrderRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable).map(this::toPoDto);
    }

    @Transactional
    public PurchaseOrderDto createPurchaseOrder(CreatePurchaseOrderRequest request) {
        String companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        Branch branch = branchRepository.findByIdAndDeletedAtIsNull(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", request.getBranchId()));

        Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", request.getSupplierId()));

        User user = userRepository.findByIdAndDeletedAtIsNull(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User not found"));

        String orderNumber = "PO-" + System.currentTimeMillis();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        PurchaseOrder po = PurchaseOrder.builder()
                .company(company)
                .branch(branch)
                .supplier(supplier)
                .orderNumber(orderNumber)
                .issueDate(request.getIssueDate())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .notes(request.getNotes())
                .status("ISSUED")
                .createdBy(user)
                .build();

        for (var itemReq : request.getItems()) {
            Product product = productRepository.findByIdAndDeletedAtIsNull(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

            BigDecimal lineTotal = itemReq.getQuantity().multiply(itemReq.getUnitCost());
            BigDecimal lineTax = lineTotal.multiply(itemReq.getTaxRate());

            subtotal = subtotal.add(lineTotal);
            taxTotal = taxTotal.add(lineTax);

            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .purchaseOrder(po)
                    .product(product)
                    .quantityOrdered(itemReq.getQuantity())
                    .quantityReceived(BigDecimal.ZERO)
                    .unitCost(itemReq.getUnitCost())
                    .taxRate(itemReq.getTaxRate())
                    .totalCost(lineTotal.add(lineTax))
                    .build();

            po.getItems().add(item);
        }

        po.setSubtotal(subtotal);
        po.setTaxAmount(taxTotal);
        po.setTotalAmount(subtotal.add(taxTotal));

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return toPoDto(saved);
    }

    @Transactional
    public void receiveGoods(CreateGoodsReceiptRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", request.getPurchaseOrderId()));

        Warehouse warehouse = warehouseRepository.findByIdAndDeletedAtIsNull(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getWarehouseId()));

        User user = userRepository.findByIdAndDeletedAtIsNull(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User not found"));

        GoodsReceipt receipt = GoodsReceipt.builder()
                .purchaseOrder(po)
                .warehouse(warehouse)
                .receiptNumber("REC-" + System.currentTimeMillis())
                .supplierInvoiceNumber(request.getSupplierInvoiceNumber())
                .notes(request.getNotes())
                .receivedDate(Instant.now())
                .createdBy(user)
                .build();

        for (var itemReq : request.getItems()) {
            Product product = productRepository.findByIdAndDeletedAtIsNull(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

            // Update Stock Level
            StockLevel stock = stockLevelRepository.findForUpdate(warehouse.getId(), product.getId())
                    .orElseGet(() -> StockLevel.builder()
                            .warehouse(warehouse)
                            .product(product)
                            .quantityAvailable(BigDecimal.ZERO)
                            .quantityReserved(BigDecimal.ZERO)
                            .quantityInTransit(BigDecimal.ZERO)
                            .build());

            stock.setQuantityAvailable(stock.getQuantityAvailable().add(itemReq.getQuantityReceived()));
            stockLevelRepository.save(stock);

            // Record Kardex movement
            StockMovement movement = StockMovement.builder()
                    .warehouse(warehouse)
                    .product(product)
                    .movementType("PURCHASE_IN")
                    .quantity(itemReq.getQuantityReceived())
                    .unitCost(itemReq.getUnitCost())
                    .balanceAfter(stock.getQuantityAvailable())
                    .referenceType("PURCHASE_ORDER")
                    .referenceId(po.getId())
                    .notes("Goods received for PO " + po.getOrderNumber())
                    .createdBy(user)
                    .build();
            stockMovementRepository.save(movement);

            // Update PO Item quantity received
            po.getItems().stream()
                    .filter(poi -> poi.getProduct().getId().equals(product.getId()))
                    .findFirst()
                    .ifPresent(poi -> poi.setQuantityReceived(poi.getQuantityReceived().add(itemReq.getQuantityReceived())));
        }

        goodsReceiptRepository.save(receipt);
        po.setStatus("COMPLETED");
        purchaseOrderRepository.save(po);
    }

    private SupplierDto toSupplierDto(Supplier s) {
        return SupplierDto.builder()
                .id(s.getId())
                .taxId(s.getTaxId())
                .name(s.getName())
                .contactName(s.getContactName())
                .email(s.getEmail())
                .phone(s.getPhone())
                .address(s.getAddress())
                .paymentTermsDays(s.getPaymentTermsDays())
                .isActive(Boolean.TRUE.equals(s.getIsActive()))
                .createdAt(s.getCreatedAt())
                .build();
    }

    private PurchaseOrderDto toPoDto(PurchaseOrder po) {
        List<PurchaseOrderItemDto> itemDtos = po.getItems().stream()
                .map(i -> PurchaseOrderItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productSku(i.getProduct().getSku())
                        .productName(i.getProduct().getName())
                        .quantityOrdered(i.getQuantityOrdered())
                        .quantityReceived(i.getQuantityReceived())
                        .unitCost(i.getUnitCost())
                        .taxRate(i.getTaxRate())
                        .totalCost(i.getTotalCost())
                        .build())
                .toList();

        return PurchaseOrderDto.builder()
                .id(po.getId())
                .branchId(po.getBranch().getId())
                .branchName(po.getBranch().getName())
                .supplierId(po.getSupplier().getId())
                .supplierName(po.getSupplier().getName())
                .orderNumber(po.getOrderNumber())
                .issueDate(po.getIssueDate())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .subtotal(po.getSubtotal())
                .taxAmount(po.getTaxAmount())
                .totalAmount(po.getTotalAmount())
                .status(po.getStatus())
                .notes(po.getNotes())
                .createdByName(po.getCreatedBy().getFullName())
                .items(itemDtos)
                .createdAt(po.getCreatedAt())
                .build();
    }
}
