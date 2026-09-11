package com.boxy.boxy.modules.purchasing.service;

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
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import com.boxy.boxy.modules.purchasing.dto.CreateGoodsReceiptRequest;
import com.boxy.boxy.modules.purchasing.dto.CreatePurchaseOrderRequest;
import com.boxy.boxy.modules.purchasing.dto.GoodsReceiptDto;
import com.boxy.boxy.modules.purchasing.dto.PurchaseOrderDto;
import com.boxy.boxy.modules.purchasing.dto.SupplierDto;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrderDto result = purchasingService.createPurchaseOrder(request);

        assertThat(result.getExpectedDeliveryDate()).isEqualTo(expected);
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

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .product(product)
                .quantityOrdered(new BigDecimal("5"))
                .quantityReceived(BigDecimal.ZERO)
                .build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L)
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
        when(goodsReceiptRepository.save(any(GoodsReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GoodsReceiptDto result = purchasingService.receiveGoods(request);

        assertThat(result.getLines()).hasSize(1);
        assertThat(result.getLines().get(0).getUnitCost()).isEqualByComparingTo(expectedUnitCost);
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
}
