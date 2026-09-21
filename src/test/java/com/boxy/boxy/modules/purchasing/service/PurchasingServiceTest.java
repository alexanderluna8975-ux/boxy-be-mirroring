package com.boxy.boxy.modules.purchasing.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.sequence.DocumentSequenceService;
import com.boxy.boxy.core.sequence.DocumentType;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.BranchRepository;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.entity.ProductCostHistory;
import com.boxy.boxy.modules.catalog.repository.ProductCostHistoryRepository;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import com.boxy.boxy.modules.purchasing.dto.CreateGoodsReceiptRequest;
import com.boxy.boxy.modules.purchasing.dto.CreatePurchaseOrderRequest;
import com.boxy.boxy.modules.purchasing.dto.GoodsReceiptDto;
import com.boxy.boxy.modules.purchasing.dto.PurchaseOrderDto;
import com.boxy.boxy.modules.purchasing.dto.SupplierDto;
import com.boxy.boxy.modules.purchasing.dto.UpdatePurchaseOrderLineRequest;
import com.boxy.boxy.modules.purchasing.entity.GoodsReceipt;
import com.boxy.boxy.modules.purchasing.entity.PurchaseOrder;
import com.boxy.boxy.modules.purchasing.entity.PurchaseOrderItem;
import com.boxy.boxy.modules.purchasing.entity.Supplier;
import com.boxy.boxy.modules.purchasing.repository.GoodsReceiptRepository;
import com.boxy.boxy.modules.purchasing.repository.PurchaseOrderRepository;
import com.boxy.boxy.modules.purchasing.repository.SupplierRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for "Fecha esperada" — the FE sends the calendar date under
 * the alias `expectedDate` (see {@code CreatePurchaseOrderRequest}'s `@JsonAlias`),
 * and it must survive the full round trip: request → entity → saved → response DTO.
 */
@ExtendWith(MockitoExtension.class)
class PurchasingServiceTest {

    @Mock private SupplierRepository supplierRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private GoodsReceiptRepository goodsReceiptRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StockLevelRepository stockLevelRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private ProductCostHistoryRepository productCostHistoryRepository;

    @InjectMocks
    private PurchasingService purchasingService;

    @Test
    void jsonAliasDeserializesTheFrontendFieldNameOntoExpectedDeliveryDate() throws Exception {
        // The FE payload key is `expectedDate`; the DTO field is `expectedDeliveryDate`.
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        String json = "{\"supplierId\":1,\"expectedDate\":\"2026-03-01\"}";

        CreatePurchaseOrderRequest request = objectMapper.readValue(json, CreatePurchaseOrderRequest.class);

        assertThat(request.getExpectedDeliveryDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void createPurchaseOrderPersistsAndReturnsTheExpectedDeliveryDate() {
        LocalDate expected = LocalDate.of(2026, 3, 1);

        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest();
        request.setSupplierId(1L);
        request.setExpectedDeliveryDate(expected);
        request.setItems(Collections.emptyList());

        Company company = Company.builder().id(1L).build();
        Branch branch = Branch.builder().id(1L).build();
        Supplier supplier = Supplier.builder().id(1L).build();
        User user = User.builder().id(1L).build();

        when(companyRepository.findById(any())).thenReturn(Optional.of(company));
        when(branchRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(branch));
        when(supplierRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(supplier));
        when(userRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(user));
        when(documentSequenceService.nextFolio(1L, DocumentType.PURCHASE_ORDER)).thenReturn("PO-00001");
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrderDto result = purchasingService.createPurchaseOrder(request);

        assertThat(result.getExpectedDeliveryDate()).isEqualTo(expected);
        // Correlative, sourced from DocumentSequenceService — not System.currentTimeMillis().
        assertThat(result.getOrderNumber()).isEqualTo("PO-00001");
    }

    /**
     * Regression for "Costo unitario" in the goods-receipt detail table:
     * {@code GoodsReceiptDto.GoodsReceiptLineDto} used to omit {@code unitCost}
     * entirely, so the line always priced at $0.00 even though the entity
     * ({@code GoodsReceiptItem}) records the real cost.
     */
    @Test
    void receiveGoodsReturnsTheUnitCostOnEachReceiptLine() {
        BigDecimal expectedUnitCost = new BigDecimal("42.50");

        Product product = Product.builder().id(10L).sku("SKU-1").name("Producto 1").build();
        Warehouse warehouse = Warehouse.builder().id(1L).build();
        User user = User.builder().id(1L).build();
        Company company = Company.builder().id(1L).build();

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .product(product)
                .quantityOrdered(new BigDecimal("5"))
                .quantityReceived(BigDecimal.ZERO)
                .build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L)
                .company(company)
                .orderNumber("PO-1")
                .items(new ArrayList<>(List.of(poItem)))
                .build();

        CreateGoodsReceiptRequest.GoodsReceiptItemRequest itemRequest =
                new CreateGoodsReceiptRequest.GoodsReceiptItemRequest();
        itemRequest.setProductId(10L);
        itemRequest.setQuantityReceived(new BigDecimal("5"));
        itemRequest.setUnitCost(expectedUnitCost);

        CreateGoodsReceiptRequest request = new CreateGoodsReceiptRequest();
        request.setPurchaseOrderId(1L);
        request.setItems(List.of(itemRequest));

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(warehouseRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.of(warehouse));
        when(userRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(user));
        when(productRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(product));
        when(stockLevelRepository.findForUpdate(any(), any())).thenReturn(Optional.empty());
        when(stockLevelRepository.getTotalAvailableStockByProductId(10L)).thenReturn(BigDecimal.ZERO);
        when(documentSequenceService.nextFolio(1L, DocumentType.GOODS_RECEIPT)).thenReturn("REC-00001");
        when(goodsReceiptRepository.save(any(GoodsReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GoodsReceiptDto result = purchasingService.receiveGoods(request);

        assertThat(result.getLines()).hasSize(1);
        assertThat(result.getLines().get(0).getUnitCost()).isEqualByComparingTo(expectedUnitCost);
        // Correlative, sourced from DocumentSequenceService — not System.currentTimeMillis().
        assertThat(result.getFolio()).isEqualTo("REC-00001");
    }

    /**
     * Regression for the new "cost history": receiving goods must now update
     * {@code Product.costPrice} to the weighted-average cost across every warehouse,
     * and save one {@code ProductCostHistory} row recording the before/after.
     */
    @Test
    void receiveGoodsUpdatesProductCostPriceToTheWeightedAverage() {
        // 10 already on hand at Bs. 20, receiving 10 more at Bs. 30 -> average Bs. 25.
        Product product = Product.builder().id(10L).sku("SKU-1").name("Producto 1")
                .costPrice(new BigDecimal("20.00")).build();
        Warehouse warehouse = Warehouse.builder().id(1L).build();
        User user = User.builder().id(1L).build();
        Company company = Company.builder().id(1L).build();

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .product(product)
                .quantityOrdered(new BigDecimal("10"))
                .quantityReceived(BigDecimal.ZERO)
                .build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L)
                .company(company)
                .orderNumber("PO-1")
                .items(new ArrayList<>(List.of(poItem)))
                .build();

        CreateGoodsReceiptRequest.GoodsReceiptItemRequest itemRequest =
                new CreateGoodsReceiptRequest.GoodsReceiptItemRequest();
        itemRequest.setProductId(10L);
        itemRequest.setQuantityReceived(new BigDecimal("10"));
        itemRequest.setUnitCost(new BigDecimal("30.00"));

        CreateGoodsReceiptRequest request = new CreateGoodsReceiptRequest();
        request.setPurchaseOrderId(1L);
        request.setItems(List.of(itemRequest));

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(warehouseRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.of(warehouse));
        when(userRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(user));
        when(productRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(product));
        when(stockLevelRepository.findForUpdate(any(), any())).thenReturn(Optional.empty());
        when(stockLevelRepository.getTotalAvailableStockByProductId(10L)).thenReturn(new BigDecimal("10"));
        when(documentSequenceService.nextFolio(1L, DocumentType.GOODS_RECEIPT)).thenReturn("REC-00002");
        when(goodsReceiptRepository.save(any(GoodsReceipt.class)))
                .thenAnswer(invocation -> {
                    GoodsReceipt receipt = invocation.getArgument(0);
                    receipt.setId(99L);
                    return receipt;
                });

        purchasingService.receiveGoods(request);

        assertThat(product.getCostPrice()).isEqualByComparingTo("25.0000");
        // Unlike costPrice (the blended average above), this is exactly what was paid this time.
        assertThat(product.getLastPurchaseCost()).isEqualByComparingTo("30.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductCostHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(productCostHistoryRepository).saveAll(captor.capture());
        ProductCostHistory history = captor.getValue().get(0);
        assertThat(history.getPreviousCost()).isEqualByComparingTo("20.00");
        assertThat(history.getNewCost()).isEqualByComparingTo("25.0000");
        assertThat(history.getUnitCost()).isEqualByComparingTo("30.00");
        assertThat(history.getQuantityReceived()).isEqualByComparingTo("10");
        assertThat(history.getGoodsReceiptId()).isEqualTo(99L);
        assertThat(history.getGoodsReceiptNumber()).isEqualTo("REC-00002");
    }

    /**
     * Approving now IS sending — lands straight on ORDERED, no separate "Marcar
     * como Enviada" step left for the reviewer to click through.
     */
    @Test
    void approvePurchaseOrderLandsStraightOnOrdered() {
        PurchaseOrder po = PurchaseOrder.builder().id(1L).status("SUBMITTED").items(new ArrayList<>()).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrderDto result = purchasingService.approvePurchaseOrder(1L);

        assertThat(result.getStatus()).isEqualTo("ordered");
    }

    /**
     * Reviewers can correct a mistyped quantity/cost inline before approving — quantity, cost,
     * the recomputed line total, and the order's own subtotal/tax/total must all update together.
     */
    @Test
    void updatePurchaseOrderLineRecalculatesLineAndOrderTotals() {
        Product product = Product.builder().id(10L).sku("SKU-1").name("Producto 1")
                .sellingPrice(new BigDecimal("50.00")).build();
        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .id(1L).product(product)
                .quantityOrdered(new BigDecimal("5")).unitCost(new BigDecimal("10.00"))
                .taxRate(new BigDecimal("0.16")).totalCost(new BigDecimal("58.00"))
                .build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L).status("SUBMITTED").items(new ArrayList<>(List.of(item))).build();

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePurchaseOrderLineRequest request = new UpdatePurchaseOrderLineRequest();
        request.setQuantity(new BigDecimal("8"));
        request.setUnitCost(new BigDecimal("12.00"));

        PurchaseOrderDto result = purchasingService.updatePurchaseOrderLine(1L, 10L, request);

        assertThat(result.getItems().get(0).getQuantityOrdered()).isEqualByComparingTo("8");
        assertThat(result.getItems().get(0).getUnitCost()).isEqualByComparingTo("12.00");
        // 8 * 12 = 96, + 16% tax = 111.36
        assertThat(result.getItems().get(0).getTotalCost()).isEqualByComparingTo("111.36");
        assertThat(result.getSubtotal()).isEqualByComparingTo("96.00");
        assertThat(result.getTaxAmount()).isEqualByComparingTo("15.36");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("111.36");
        // salePrice wasn't sent — the product's sale price stays untouched.
        assertThat(product.getSellingPrice()).isEqualByComparingTo("50.00");
        verify(productRepository, never()).save(any());
    }

    @Test
    void updatePurchaseOrderLineUpdatesTheProductSalePriceWhenGiven() {
        Product product = Product.builder().id(10L).sku("SKU-1").name("Producto 1")
                .sellingPrice(new BigDecimal("50.00")).build();
        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .id(1L).product(product)
                .quantityOrdered(new BigDecimal("5")).unitCost(new BigDecimal("10.00"))
                .taxRate(BigDecimal.ZERO).totalCost(new BigDecimal("50.00"))
                .build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L).status("SUBMITTED").items(new ArrayList<>(List.of(item))).build();

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePurchaseOrderLineRequest request = new UpdatePurchaseOrderLineRequest();
        request.setQuantity(new BigDecimal("5"));
        request.setUnitCost(new BigDecimal("10.00"));
        request.setSalePrice(new BigDecimal("65.00"));

        PurchaseOrderDto result = purchasingService.updatePurchaseOrderLine(1L, 10L, request);

        assertThat(product.getSellingPrice()).isEqualByComparingTo("65.00");
        assertThat(result.getItems().get(0).getSalePrice()).isEqualByComparingTo("65.00");
        verify(productRepository).save(product);
    }

    @Test
    void updatePurchaseOrderLineRejectedOutsideSubmitted() {
        PurchaseOrder po = PurchaseOrder.builder().id(1L).status("ORDERED").items(new ArrayList<>()).build();
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        UpdatePurchaseOrderLineRequest request = new UpdatePurchaseOrderLineRequest();
        request.setQuantity(BigDecimal.TEN);
        request.setUnitCost(BigDecimal.ONE);

        assertThatThrownBy(() -> purchasingService.updatePurchaseOrderLine(1L, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ORDERED");
    }

    /**
     * Regression for the suppliers list: `code` was never sent (`suppliers` has no
     * such column), and `orderCount`/`lastOrderAt`/`totalPurchased` were hardcoded
     * to 0/null instead of being computed from the supplier's purchase orders.
     */
    @Test
    void getSupplierByIdComputesCodeOrderCountAndLastOrderDate() {
        Supplier supplier = Supplier.builder().id(7L).name("ACME").isActive(true).build();
        Instant lastOrder = Instant.parse("2026-02-15T10:00:00Z");

        when(supplierRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.countBySupplierId(7L)).thenReturn(3L);
        when(purchaseOrderRepository.findLastOrderDateBySupplierId(7L)).thenReturn(lastOrder);
        when(purchaseOrderRepository.sumTotalAmountBySupplierId(7L)).thenReturn(new BigDecimal("1250.00"));

        SupplierDto result = purchasingService.getSupplierById(7L);

        assertThat(result.getCode()).isEqualTo("PRV-0007");
        assertThat(result.getOrderCount()).isEqualTo(3);
        assertThat(result.getLastOrderAt()).isEqualTo(lastOrder);
        assertThat(result.getTotalPurchased()).isEqualByComparingTo(new BigDecimal("1250.00"));
    }

    /**
     * Receiving must only offer orders actually sent to the supplier — DRAFT/SUBMITTED (still
     * pending approval) and REJECTED have nothing to receive against yet, and RECEIVED/CANCELLED
     * are done. Regression: the filter used to only exclude RECEIVED/CANCELLED, so a purchasing
     * officer could see (and try to receive against) an order still awaiting approval.
     */
    @Test
    void getPendingPurchaseOrdersOnlyReturnsOrdersActuallySentToTheSupplier() {
        PurchaseOrder draft = PurchaseOrder.builder().id(1L).status("DRAFT").items(new ArrayList<>()).build();
        PurchaseOrder submitted = PurchaseOrder.builder().id(2L).status("SUBMITTED").items(new ArrayList<>()).build();
        PurchaseOrder rejected = PurchaseOrder.builder().id(3L).status("REJECTED").items(new ArrayList<>()).build();
        PurchaseOrder ordered = PurchaseOrder.builder().id(4L).status("ORDERED").items(new ArrayList<>()).build();
        PurchaseOrder partiallyReceived =
                PurchaseOrder.builder().id(5L).status("PARTIALLY_RECEIVED").items(new ArrayList<>()).build();
        PurchaseOrder received = PurchaseOrder.builder().id(6L).status("RECEIVED").items(new ArrayList<>()).build();
        PurchaseOrder cancelled = PurchaseOrder.builder().id(7L).status("CANCELLED").items(new ArrayList<>()).build();

        when(purchaseOrderRepository.findByCompanyId(1L))
                .thenReturn(List.of(draft, submitted, rejected, ordered, partiallyReceived, received, cancelled));

        List<PurchaseOrderDto> result = purchasingService.getPendingPurchaseOrders();

        assertThat(result).extracting(PurchaseOrderDto::getId).containsExactlyInAnyOrder(4L, 5L);
    }
}
