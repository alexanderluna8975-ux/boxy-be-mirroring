package com.boxy.boxy.modules.inventory.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.InsufficientStockException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.core.security.UserPrincipal;
import com.boxy.boxy.core.sequence.DocumentSequenceService;
import com.boxy.boxy.core.sequence.DocumentType;
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
import com.boxy.boxy.modules.inventory.dto.ReceiveTransferRequest;
import com.boxy.boxy.modules.inventory.dto.StockTransferDto;
import com.boxy.boxy.modules.inventory.dto.UpdateTransferLineRequest;
import com.boxy.boxy.modules.inventory.entity.StockLevel;
import com.boxy.boxy.modules.inventory.entity.StockMovement;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the transfer state machine. {@code approveTransfer} now folds in what used to be a
 * separate {@code dispatchTransfer} step (no more double action to send — approving moves stock
 * immediately and lands on {@code IN_TRANSIT}), and {@code receiveTransfer} accepts a per-line
 * actual-received quantity instead of blindly trusting what shipped.
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
    @Mock
    private DocumentSequenceService documentSequenceService;

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
        when(documentSequenceService.nextFolio(COMPANY_ID, DocumentType.TRANSFER)).thenReturn("TRF-00001");

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
        when(documentSequenceService.nextFolio(COMPANY_ID, DocumentType.TRANSFER)).thenReturn("TRF-00007");
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto dto = inventoryService.createTransfer(request);

        assertThat(dto.getStatus()).isEqualTo("pending-approval");
        assertThat(dto.getSourceWarehouseId()).isEqualTo(1L);
        assertThat(dto.getDestinationWarehouseId()).isEqualTo(2L);
        // Correlative, sourced from DocumentSequenceService — not a UUID fragment.
        assertThat(dto.getFolio()).isEqualTo("TRF-00007");
    }

    // ---- updateTransferLine / deleteTransferLine (inline edit before approval) ----

    @Test
    void updateTransferLine_onlyFromRequested() {
        StockTransfer approved = transferInStatus(TransferStatus.APPROVED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> inventoryService.updateTransferLine(10L, 1L, updateLineRequest(BigDecimal.valueOf(15))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REQUESTED");
    }

    @Test
    void updateTransferLine_correctsTheQuantity() {
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto dto = inventoryService.updateTransferLine(10L, 1L, updateLineRequest(BigDecimal.valueOf(15)));

        assertThat(dto.getItems().get(0).getQuantityRequested()).isEqualByComparingTo("15");
    }

    @Test
    void updateTransferLine_rejectsZeroOrNegativeQuantity() {
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> inventoryService.updateTransferLine(10L, 1L, updateLineRequest(BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("greater than zero");

        verify(stockTransferRepository, never()).save(any());
    }

    @Test
    void updateTransferLine_rejectsUnknownProduct() {
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> inventoryService.updateTransferLine(10L, 99L, updateLineRequest(BigDecimal.TEN)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private UpdateTransferLineRequest updateLineRequest(BigDecimal quantity) {
        UpdateTransferLineRequest request = new UpdateTransferLineRequest();
        request.setQuantity(quantity);
        return request;
    }

    @Test
    void deleteTransferLine_onlyFromRequested() {
        StockTransfer approved = transferInStatus(TransferStatus.APPROVED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> inventoryService.deleteTransferLine(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REQUESTED");
    }

    @Test
    void deleteTransferLine_refusesToRemoveTheLastLine() {
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> inventoryService.deleteTransferLine(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one line item");

        verify(stockTransferRepository, never()).save(any());
    }

    @Test
    void deleteTransferLine_removesTheLineWhenMoreThanOneRemains() {
        Product secondProduct = Product.builder().id(2L).company(company).sku("SKU-2").name("Gadget")
                .costPrice(BigDecimal.ONE).sellingPrice(BigDecimal.valueOf(5)).hasVariants(false).build();
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        requested.getItems().add(StockTransferItem.builder()
                .id(2L).transfer(requested).product(secondProduct)
                .quantityRequested(BigDecimal.valueOf(4)).quantityReceived(BigDecimal.ZERO).build());
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto dto = inventoryService.deleteTransferLine(10L, 1L);

        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getItems().get(0).getProductId()).isEqualTo(2L);
    }

    @Test
    void deleteTransferLine_rejectsUnknownProduct() {
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        requested.getItems().add(StockTransferItem.builder()
                .id(2L).transfer(requested).product(product)
                .quantityRequested(BigDecimal.valueOf(4)).quantityReceived(BigDecimal.ZERO).build());
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> inventoryService.deleteTransferLine(10L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- approveTransfer (now also dispatches — no separate ship step) ----

    @Test
    void approveTransfer_onlyFromRequested() {
        StockTransfer approved = transferInStatus(TransferStatus.APPROVED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> inventoryService.approveTransfer(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REQUESTED");
    }

    @Test
    void approveTransfer_dispatchesImmediately_oneActionNotTwo() {
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        StockLevel stock = StockLevel.builder().id(1L).warehouse(source).product(product)
                .quantityAvailable(BigDecimal.valueOf(50)).quantityReserved(BigDecimal.ZERO)
                .quantityInTransit(BigDecimal.ZERO).build();
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(stock));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto dto = inventoryService.approveTransfer(10L);

        // Status lands straight on "shipped" (IN_TRANSIT) — "approved" is never the persisted
        // status a user has to act on a second time.
        assertThat(dto.getStatus()).isEqualTo("shipped");
        assertThat(requested.getApprovedBy()).isEqualTo(1L);
        assertThat(requested.getApprovedAt()).isNotNull();
        assertThat(requested.getDispatchedBy()).isEqualTo(1L);
        assertThat(requested.getDispatchedAt()).isEqualTo(requested.getApprovedAt());
        assertThat(stock.getQuantityAvailable()).isEqualByComparingTo("40");
        assertThat(stock.getQuantityInTransit()).isEqualByComparingTo("10");
        verify(stockMovementRepository).save(any());
    }

    @Test
    void approveTransfer_rejectsWhenStockRecordMissing() {
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.approveTransfer(10L))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void approveTransfer_rejectsWhenNotEnoughAvailable() {
        StockTransfer requested = transferInStatus(TransferStatus.REQUESTED, BigDecimal.valueOf(100));
        StockLevel stock = StockLevel.builder().id(1L).warehouse(source).product(product)
                .quantityAvailable(BigDecimal.TEN).quantityReserved(BigDecimal.ZERO)
                .quantityInTransit(BigDecimal.ZERO).build();
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(requested));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> inventoryService.approveTransfer(10L))
                .isInstanceOf(InsufficientStockException.class);

        verify(stockLevelRepository, never()).save(any());
    }

    // ---- receiveTransfer ----

    @Test
    void receiveTransfer_onlyFromInTransit() {
        StockTransfer approved = transferInStatus(TransferStatus.APPROVED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> inventoryService.receiveTransfer(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("IN_TRANSIT");
    }

    @Test
    void receiveTransfer_nullBody_defaultsEveryLineToFullyReceived() {
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

        StockTransferDto dto = inventoryService.receiveTransfer(10L, null);

        assertThat(dto.getStatus()).isEqualTo("received");
        assertThat(sourceStock.getQuantityInTransit()).isEqualByComparingTo("0");
        assertThat(dto.getItems().get(0).getQuantityReceived()).isEqualByComparingTo("10");
        // No shortage → no TRANSFER_LOSS entry, only the TRANSFER_IN credit.
        verify(stockMovementRepository, times(1)).save(any());
    }

    @Test
    void receiveTransfer_failsCleanlyWhenSourceStockRowIsGone() {
        StockTransfer inTransit = transferInStatus(TransferStatus.IN_TRANSIT, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(inTransit));
        when(stockLevelRepository.findForUpdate(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.receiveTransfer(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No stock record at the source warehouse");
    }

    @Test
    void receiveTransfer_shortageWithoutNote_isRejected() {
        StockTransfer inTransit = transferInStatus(TransferStatus.IN_TRANSIT, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(inTransit));
        ReceiveTransferRequest request = shortageRequest(BigDecimal.valueOf(7), null);

        assertThatThrownBy(() -> inventoryService.receiveTransfer(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("note is required");

        // Validated before touching anything.
        verify(stockLevelRepository, never()).findForUpdate(any(), any());
    }

    @Test
    void receiveTransfer_shortageWithNote_creditsOnlyWhatArrivedAndLogsTheLoss() {
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
        ReceiveTransferRequest request = shortageRequest(BigDecimal.valueOf(7), "Caja dañada en tránsito.");

        StockTransferDto dto = inventoryService.receiveTransfer(10L, request);

        assertThat(dto.getStatus()).isEqualTo("received");
        // Full shipped amount leaves in-transit either way (arrived or lost).
        assertThat(sourceStock.getQuantityInTransit()).isEqualByComparingTo("0");
        assertThat(dto.getItems().get(0).getQuantityReceived()).isEqualByComparingTo("7");
        assertThat(dto.getReceivingNotes()).isEqualTo("Caja dañada en tránsito.");
        // TRANSFER_IN (7) + TRANSFER_LOSS (3) — the gap is logged, not silently dropped.
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());
        StockMovement loss = captor.getAllValues().stream()
                .filter(m -> "TRANSFER_LOSS".equals(m.getMovementType())).findFirst().orElseThrow();
        assertThat(loss.getQuantity()).isEqualByComparingTo("-3");
        assertThat(loss.getNotes()).isEqualTo("Caja dañada en tránsito.");
    }

    @Test
    void receiveTransfer_overageWithoutNote_isRejected() {
        StockTransfer inTransit = transferInStatus(TransferStatus.IN_TRANSIT, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(inTransit));
        ReceiveTransferRequest request = shortageRequest(BigDecimal.valueOf(11), null);

        assertThatThrownBy(() -> inventoryService.receiveTransfer(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("note is required");

        verify(stockLevelRepository, never()).findForUpdate(any(), any());
    }

    @Test
    void receiveTransfer_overageWithNote_deductsTheExtraFromSourceAvailableAndLogsIt() {
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
        ReceiveTransferRequest request = shortageRequest(BigDecimal.valueOf(12), "Llegaron 2 unidades de más.");

        StockTransferDto dto = inventoryService.receiveTransfer(10L, request);

        assertThat(dto.getStatus()).isEqualTo("received");
        assertThat(dto.getItems().get(0).getQuantityReceived()).isEqualByComparingTo("12");
        // Full shipped amount (10) leaves in-transit, then the extra 2 also leaves available.
        assertThat(sourceStock.getQuantityInTransit()).isEqualByComparingTo("0");
        assertThat(sourceStock.getQuantityAvailable()).isEqualByComparingTo("38");
        assertThat(dto.getReceivingNotes()).isEqualTo("Llegaron 2 unidades de más.");
        // TRANSFER_IN (12) + TRANSFER_OUT (the 2-unit overage) — the extra is logged, not silently absorbed.
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());
        StockMovement overage = captor.getAllValues().stream()
                .filter(m -> "TRANSFER_OUT".equals(m.getMovementType())).findFirst().orElseThrow();
        assertThat(overage.getQuantity()).isEqualByComparingTo("-2");
        assertThat(overage.getNotes()).isEqualTo("Llegaron 2 unidades de más.");
    }

    @Test
    void receiveTransfer_rejectsNegativeReceivedQuantity() {
        StockTransfer inTransit = transferInStatus(TransferStatus.IN_TRANSIT, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(inTransit));
        ReceiveTransferRequest request = shortageRequest(BigDecimal.valueOf(-1), "cualquier nota");

        assertThatThrownBy(() -> inventoryService.receiveTransfer(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be negative");
    }

    private ReceiveTransferRequest shortageRequest(BigDecimal receivedQuantity, String notes) {
        ReceiveTransferRequest.ReceiveItemRequest line = new ReceiveTransferRequest.ReceiveItemRequest();
        line.setProductId(1L);
        line.setQuantityReceived(receivedQuantity);
        ReceiveTransferRequest request = new ReceiveTransferRequest();
        request.setItems(List.of(line));
        request.setNotes(notes);
        return request;
    }

    // ---- rejectTransfer ----

    @Test
    void rejectTransfer_allowedFromRequested() {
        StockTransfer transfer = transferInStatus(TransferStatus.REQUESTED, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(transfer));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        StockTransferDto dto = inventoryService.rejectTransfer(10L);

        assertThat(dto.getStatus()).isEqualTo("rejected");
    }

    // Now that approving dispatches immediately, APPROVED is never a persisted state a real
    // transfer sits in — but the guard still defends the (unreachable in practice, reachable in
    // a test) case a row is fabricated in that status, same as it defends IN_TRANSIT.
    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"APPROVED", "IN_TRANSIT"})
    void rejectTransfer_blockedOnceApprovedOrLater(TransferStatus status) {
        StockTransfer transfer = transferInStatus(status, BigDecimal.TEN);
        when(stockTransferRepository.findByIdAndCompanyId(10L, COMPANY_ID)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> inventoryService.rejectTransfer(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already in transit");

        verify(stockTransferRepository, times(0)).save(eq(transfer));
    }

    // ---- multi-tenant isolation ----

    @Test
    void getTransferById_returns404StyleErrorForAnotherCompanysTransfer() {
        when(stockTransferRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getTransferById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
