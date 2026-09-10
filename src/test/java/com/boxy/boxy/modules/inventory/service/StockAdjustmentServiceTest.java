package com.boxy.boxy.modules.inventory.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.core.security.UserPrincipal;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockAdjustmentServiceTest {

    @Mock
    private StockAdjustmentRepository stockAdjustmentRepository;
    @Mock(answer = org.mockito.Answers.CALLS_REAL_METHODS)
    private StockLevelRepository stockLevelRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private StockAdjustmentService service;

    private static final Long COMPANY_ID = 1L;
    private Company company;
    private Warehouse warehouse;
    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        company = Company.builder().id(COMPANY_ID).name("Acme").taxId("TAX-1").build();
        Branch branch = Branch.builder().id(1L).company(company).code("BR").name("Main Branch").build();
        warehouse = Warehouse.builder().id(1L).branch(branch).code("WH-1").name("Main Warehouse").build();
        user = User.builder().id(1L).company(company).username("tester").firstName("Test").lastName("User").build();
        product = Product.builder().id(1L).company(company).sku("SKU-1").name("Widget")
                .costPrice(BigDecimal.TEN).sellingPrice(BigDecimal.valueOf(20)).hasVariants(false).build();

        UserPrincipal principal = UserPrincipal.create(user.getId(), COMPANY_ID, "tester", "tester@boxy.dev",
                "x", "Test User", 1L, "ACTIVE", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private StockAdjustment adjustmentInStatus(AdjustmentStatus status, BigDecimal delta) {
        StockAdjustment adjustment = StockAdjustment.builder()
                .id(20L)
                .company(company)
                .warehouse(warehouse)
                .adjustmentNumber("ADJ-TEST")
                .reason("Conteo físico / Inventario cíclico")
                .status(status)
                .createdBy(user)
                .items(new ArrayList<>())
                .build();
        StockAdjustmentItem item = StockAdjustmentItem.builder()
                .id(1L)
                .adjustment(adjustment)
                .product(product)
                .previousQuantity(BigDecimal.valueOf(30))
                .newQuantity(BigDecimal.valueOf(30).add(delta))
                .differenceQuantity(delta)
                .unitCost(BigDecimal.TEN)
                .build();
        adjustment.getItems().add(item);
        return adjustment;
    }

    // ---- createAdjustment ----

    @Test
    void createAdjustment_rejectsEmptyLines() {
        CreateStockAdjustmentRequest request = CreateStockAdjustmentRequest.builder()
                .warehouseId(1L).reasonId("1").lines(List.of()).build();
        when(warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.createAdjustment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one line item");
    }

    @Test
    void createAdjustment_rejectsProductWithVariants() {
        product.setHasVariants(true);
        CreateStockAdjustmentRequest.AdjustmentLineRequest line = CreateStockAdjustmentRequest.AdjustmentLineRequest.builder()
                .productId(1L).quantityDelta(BigDecimal.valueOf(-5)).build();
        CreateStockAdjustmentRequest request = CreateStockAdjustmentRequest.builder()
                .warehouseId(1L).reasonId("1").lines(List.of(line)).build();
        when(warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(warehouse));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.createAdjustment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("variants");
    }

    @Test
    void createAdjustment_keepsClientCountSnapshotForAudit() {
        CreateStockAdjustmentRequest.AdjustmentLineRequest line = CreateStockAdjustmentRequest.AdjustmentLineRequest.builder()
                .productId(1L).quantityDelta(BigDecimal.valueOf(-5))
                .previousQuantity(BigDecimal.valueOf(30)).countedQuantity(BigDecimal.valueOf(25)).build();
        CreateStockAdjustmentRequest request = CreateStockAdjustmentRequest.builder()
                .warehouseId(1L).reasonId("2").lines(List.of(line)).build();
        when(warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(warehouse));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(product));
        when(stockAdjustmentRepository.save(any(StockAdjustment.class))).thenAnswer(inv -> inv.getArgument(0));
        // Live stock has since drifted to 100 — the stored snapshot must still reflect what was
        // actually counted (30 → 25), not this live value.
        lenient().when(stockLevelRepository.findByWarehouseIdAndProductIdAndVariantIdIsNull(1L, 1L))
                .thenReturn(Optional.of(StockLevel.builder().quantityAvailable(BigDecimal.valueOf(100)).build()));

        StockAdjustmentDto dto = service.createAdjustment(request);

        assertThat(dto.getLines().get(0).getPreviousQuantity()).isEqualByComparingTo("30");
        assertThat(dto.getLines().get(0).getNewQuantity()).isEqualByComparingTo("25");
        assertThat(dto.getStatus()).isEqualTo("pending-approval");
    }

    // ---- approveAdjustment ----

    @Test
    void approveAdjustment_onlyFromPendingApproval() {
        StockAdjustment approved = adjustmentInStatus(AdjustmentStatus.APPROVED, BigDecimal.valueOf(-5));
        when(stockAdjustmentRepository.findByIdAndCompanyId(20L, COMPANY_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approveAdjustment(20L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PENDING_APPROVAL");

        verify(stockLevelRepository, never()).save(any());
    }

    @Test
    void approveAdjustment_appliesStockExactlyOnce() {
        StockAdjustment pending = adjustmentInStatus(AdjustmentStatus.PENDING_APPROVAL, BigDecimal.valueOf(-5));
        StockLevel stock = StockLevel.builder().id(1L).warehouse(warehouse).product(product)
                .quantityAvailable(BigDecimal.valueOf(30)).quantityReserved(BigDecimal.ZERO)
                .quantityInTransit(BigDecimal.ZERO).build();
        when(stockAdjustmentRepository.findByIdAndCompanyId(20L, COMPANY_ID)).thenReturn(Optional.of(pending));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(stock));
        when(stockAdjustmentRepository.save(any(StockAdjustment.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustmentDto dto = service.approveAdjustment(20L);

        assertThat(dto.getStatus()).isEqualTo("approved");
        assertThat(stock.getQuantityAvailable()).isEqualByComparingTo("25");
        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
    }

    @Test
    void approveAdjustment_negativeMovementQuantityMatchesSign() {
        StockAdjustment pending = adjustmentInStatus(AdjustmentStatus.PENDING_APPROVAL, BigDecimal.valueOf(-5));
        StockLevel stock = StockLevel.builder().id(1L).warehouse(warehouse).product(product)
                .quantityAvailable(BigDecimal.valueOf(30)).quantityReserved(BigDecimal.ZERO)
                .quantityInTransit(BigDecimal.ZERO).build();
        when(stockAdjustmentRepository.findByIdAndCompanyId(20L, COMPANY_ID)).thenReturn(Optional.of(pending));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(stock));
        when(stockAdjustmentRepository.save(any(StockAdjustment.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);

        service.approveAdjustment(20L);

        verify(stockMovementRepository).save(captor.capture());
        StockMovement movement = captor.getValue();
        assertThat(movement.getMovementType()).isEqualTo("ADJUSTMENT_OUT");
        // Signed like every other movement type (SALE_OUT, TRANSFER_OUT), not `.abs()`.
        assertThat(movement.getQuantity()).isEqualByComparingTo("-5");
    }

    @Test
    void approveAdjustment_positiveDeltaWritesAdjustmentIn() {
        StockAdjustment pending = adjustmentInStatus(AdjustmentStatus.PENDING_APPROVAL, BigDecimal.valueOf(8));
        StockLevel stock = StockLevel.builder().id(1L).warehouse(warehouse).product(product)
                .quantityAvailable(BigDecimal.valueOf(30)).quantityReserved(BigDecimal.ZERO)
                .quantityInTransit(BigDecimal.ZERO).build();
        when(stockAdjustmentRepository.findByIdAndCompanyId(20L, COMPANY_ID)).thenReturn(Optional.of(pending));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(stock));
        when(stockAdjustmentRepository.save(any(StockAdjustment.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);

        service.approveAdjustment(20L);

        verify(stockMovementRepository).save(captor.capture());
        assertThat(captor.getValue().getMovementType()).isEqualTo("ADJUSTMENT_IN");
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("8");
        assertThat(stock.getQuantityAvailable()).isEqualByComparingTo("38");
    }

    @Test
    void approveAdjustment_rejectsWhenResultWouldGoNegative() {
        StockAdjustment pending = adjustmentInStatus(AdjustmentStatus.PENDING_APPROVAL, BigDecimal.valueOf(-100));
        StockLevel stock = StockLevel.builder().id(1L).warehouse(warehouse).product(product)
                .quantityAvailable(BigDecimal.valueOf(30)).quantityReserved(BigDecimal.ZERO)
                .quantityInTransit(BigDecimal.ZERO).build();
        when(stockAdjustmentRepository.findByIdAndCompanyId(20L, COMPANY_ID)).thenReturn(Optional.of(pending));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.approveAdjustment(20L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("below zero");

        // Not silently clamped to zero: the stock level must be left untouched.
        assertThat(stock.getQuantityAvailable()).isEqualByComparingTo("30");
        verify(stockLevelRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    // ---- rejectAdjustment ----

    @Test
    void rejectAdjustment_onlyFromPendingApproval() {
        StockAdjustment approved = adjustmentInStatus(AdjustmentStatus.APPROVED, BigDecimal.valueOf(-5));
        when(stockAdjustmentRepository.findByIdAndCompanyId(20L, COMPANY_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.rejectAdjustment(20L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PENDING_APPROVAL");
    }

    @Test
    void rejectAdjustment_happyPath() {
        StockAdjustment pending = adjustmentInStatus(AdjustmentStatus.PENDING_APPROVAL, BigDecimal.valueOf(-5));
        when(stockAdjustmentRepository.findByIdAndCompanyId(20L, COMPANY_ID)).thenReturn(Optional.of(pending));
        when(stockAdjustmentRepository.save(any(StockAdjustment.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustmentDto dto = service.rejectAdjustment(20L);

        assertThat(dto.getStatus()).isEqualTo("rejected");
        verify(stockLevelRepository, never()).save(any());
    }
}
