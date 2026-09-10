package com.boxy.boxy.modules.inventory.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.InsufficientStockException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.core.security.UserPrincipal;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.dto.CreateStockTransferRequest;
import com.boxy.boxy.modules.inventory.dto.StockTransferDto;
import com.boxy.boxy.modules.inventory.entity.StockLevel;
import com.boxy.boxy.modules.inventory.entity.StockTransfer;
import com.boxy.boxy.modules.inventory.entity.StockTransferItem;
import com.boxy.boxy.modules.inventory.entity.TransferStatus;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import com.boxy.boxy.modules.inventory.repository.StockTransferRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the transfer state machine — the P0 bug this whole pass started from was
 * {@code dispatchTransfer} requiring {@code REQUESTED} while {@code approveTransfer} left the
 * transfer at {@code APPROVED}, so every transfer got stuck right after approval. These tests
 * pin the corrected transitions down so that regression can't come back silently.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private StockLevelRepository stockLevelRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private StockTransferRepository stockTransferRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private InventoryService inventoryService;

    private static final Long COMPANY_ID = 1L;
    private Company company;
    private Warehouse source;
    private Warehouse destination;
    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        company = Company.builder().id(COMPANY_ID).name("Acme").taxId("TAX-1").build();
        Branch branch = Branch.builder().id(1L).company(company).code("BR").name("Main Branch").build();
        source = Warehouse.builder().id(1L).branch(branch).code("WH-1").name("Source").build();
        destination = Warehouse.builder().id(2L).branch(branch).code("WH-2").name("Destination").build();
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

    private CreateStockTransferRequest transferRequest(Long sourceId, Long destId, BigDecimal quantity) {
        CreateStockTransferRequest.TransferItemRequest item = new CreateStockTransferRequest.TransferItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(quantity);

        CreateStockTransferRequest request = new CreateStockTransferRequest();
        request.setSourceWarehouseId(sourceId);
        request.setDestinationWarehouseId(destId);
        request.setItems(List.of(item));
        return request;
    }

    private StockTransfer transferInStatus(TransferStatus status, BigDecimal quantity) {
        StockTransfer transfer = StockTransfer.builder()
                .id(10L)
                .company(company)
                .transferNumber("TRF-TEST")
                .sourceWarehouse(source)
                .destinationWarehouse(destination)
                .status(status)
                .requestedBy(user)
                .build();
        StockTransferItem item = StockTransferItem.builder()
                .id(1L)
                .transfer(transfer)
                .product(product)
                .quantityRequested(quantity)
                .quantityReceived(BigDecimal.ZERO)
                .build();
        transfer.getItems().add(item);
        return transfer;
    }

    // ---- createTransfer ----

    @Test
    void createTransfer_rejectsSameSourceAndDestination() {
        CreateStockTransferRequest request = transferRequest(1L, 1L, BigDecimal.TEN);

        assertThatThrownBy(() -> inventoryService.createTransfer(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be the same");

        verify(warehouseRepository, never()).findByIdAndBranchCompanyIdAndDeletedAtIsNull(anyLong(), anyLong());
    }

    @Test
    void createTransfer_rejectsEmptyItems() {
        CreateStockTransferRequest request = transferRequest(1L, 2L, BigDecimal.TEN);
        request.setItems(List.of());

        assertThatThrownBy(() -> inventoryService.createTransfer(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one line item");
    }

    @Test
    void createTransfer_rejectsProductWithVariants() {
        product.setHasVariants(true);
        CreateStockTransferRequest request = transferRequest(1L, 2L, BigDecimal.TEN);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(source));
        when(warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(2L, COMPANY_ID)).thenReturn(Optional.of(destination));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> inventoryService.createTransfer(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("variants");
    }

    @Test
    void createTransfer_happyPath_startsRequested() {
        CreateStockTransferRequest request = transferRequest(1L, 2L, BigDecimal.TEN);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(source));
        when(warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(2L, COMPANY_ID)).thenReturn(Optional.of(destination));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(product));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto dto = inventoryService.createTransfer(request);

        assertThat(dto.getStatus()).isEqualTo("pending-approval");
        assertThat(dto.getSourceWarehouseId()).isEqualTo(1L);
        assertThat(dto.getDestinationWarehouseId()).isEqualTo(2L);
    }

    // ---- approveTransfer ----

    @Test
    void approveTransfer_onlyFromRequested() {
        StockTransfer approved = transferInStatus(TransferStatus.APPROVED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> inventoryService.approveTransfer(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REQUESTED");
    }

    @Test
    void approveTransfer_setsApprovedByAndAt() {
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        StockTransferDto dto = inventoryService.approveTransfer(10L);

        assertThat(dto.getStatus()).isEqualTo("approved");
        assertThat(requested.getApprovedBy()).isEqualTo(1L);
        assertThat(requested.getApprovedAt()).isNotNull();
    }

    // ---- the regression this pass fixes: dispatch must require APPROVED, not REQUESTED ----

    @Test
    void dispatchTransfer_rejectsFromRequested_theOriginalBug() {
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> inventoryService.dispatchTransfer(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void dispatchTransfer_succeedsFromApproved() {
        StockTransfer approved = transferInStatus(TransferStatus.APPROVED, BigDecimal.TEN);
        StockLevel stock = StockLevel.builder().id(1L).warehouse(source).product(product)
                .quantityAvailable(BigDecimal.valueOf(50)).quantityReserved(BigDecimal.ZERO)
                .quantityInTransit(BigDecimal.ZERO).build();
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(approved));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(stock));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto dto = inventoryService.dispatchTransfer(10L);

        assertThat(dto.getStatus()).isEqualTo("shipped");
        assertThat(stock.getQuantityAvailable()).isEqualByComparingTo("40");
        assertThat(stock.getQuantityInTransit()).isEqualByComparingTo("10");
        verify(stockMovementRepository).save(any());
    }

    @Test
    void dispatchTransfer_rejectsWhenStockRecordMissing() {
        StockTransfer approved = transferInStatus(TransferStatus.APPROVED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(approved));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.dispatchTransfer(10L))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void dispatchTransfer_rejectsWhenNotEnoughAvailable() {
        StockTransfer approved = transferInStatus(TransferStatus.APPROVED, BigDecimal.valueOf(100));
        StockLevel stock = StockLevel.builder().id(1L).warehouse(source).product(product)
                .quantityAvailable(BigDecimal.TEN).quantityReserved(BigDecimal.ZERO)
                .quantityInTransit(BigDecimal.ZERO).build();
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(approved));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> inventoryService.dispatchTransfer(10L))
                .isInstanceOf(InsufficientStockException.class);

        verify(stockLevelRepository, never()).save(any());
    }

    // ---- receiveTransfer ----

    @Test
    void receiveTransfer_onlyFromInTransit() {
        StockTransfer approved = transferInStatus(TransferStatus.APPROVED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> inventoryService.receiveTransfer(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("IN_TRANSIT");
    }

    @Test
    void receiveTransfer_movesStockIntoDestination() {
        StockTransfer inTransit = transferInStatus(TransferStatus.IN_TRANSIT, BigDecimal.TEN);
        StockLevel sourceStock = StockLevel.builder().id(1L).warehouse(source).product(product)
                .quantityAvailable(BigDecimal.valueOf(40)).quantityReserved(BigDecimal.ZERO)
                .quantityInTransit(BigDecimal.TEN).build();
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(inTransit));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(sourceStock));
        when(stockLevelRepository.findForUpdate(2L, 1L)).thenReturn(Optional.empty());
        when(stockLevelRepository.saveAndFlush(any(StockLevel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto dto = inventoryService.receiveTransfer(10L);

        assertThat(dto.getStatus()).isEqualTo("received");
        assertThat(sourceStock.getQuantityInTransit()).isEqualByComparingTo("0");
        assertThat(dto.getItems().get(0).getQuantityReceived()).isEqualByComparingTo("10");
    }

    @Test
    void receiveTransfer_failsCleanlyWhenSourceStockRowIsGone() {
        StockTransfer inTransit = transferInStatus(TransferStatus.IN_TRANSIT, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(inTransit));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.receiveTransfer(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No stock record at the source warehouse");
    }

    // ---- rejectTransfer ----

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"REQUESTED", "APPROVED"})
    void rejectTransfer_allowedBeforeDispatch(TransferStatus status) {
        StockTransfer transfer = transferInStatus(status, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(transfer));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto dto = inventoryService.rejectTransfer(10L);

        assertThat(dto.getStatus()).isEqualTo("rejected");
    }

    @Test
    void rejectTransfer_blockedOnceInTransit() {
        StockTransfer inTransit = transferInStatus(TransferStatus.IN_TRANSIT, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(inTransit));

        assertThatThrownBy(() -> inventoryService.rejectTransfer(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dispatched");

        verify(stockTransferRepository, times(0)).save(eq(inTransit));
    }

    // ---- multi-tenant isolation ----

    @Test
    void getTransferById_returns404StyleErrorForAnotherCompanysTransfer() {
        when(stockTransferRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getTransferById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
