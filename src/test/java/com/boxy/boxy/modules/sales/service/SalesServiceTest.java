package com.boxy.boxy.modules.sales.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.realtime.events.SaleEvent;
import com.boxy.boxy.core.realtime.events.StockChange;
import com.boxy.boxy.core.security.UserPrincipal;
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
import com.boxy.boxy.modules.administration.service.AuditLogService;
import com.boxy.boxy.modules.catalog.dto.ProductDto;
import com.boxy.boxy.modules.catalog.entity.Brand;
import com.boxy.boxy.modules.catalog.entity.Category;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.entity.StockLevel;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import com.boxy.boxy.modules.sales.dto.CatalogItemDto;
import com.boxy.boxy.modules.sales.dto.CreatePriceAdjustmentRequest;
import com.boxy.boxy.modules.sales.dto.CreateQuotationRequest;
import com.boxy.boxy.modules.sales.dto.PriceAdjustmentDto;
import com.boxy.boxy.modules.sales.dto.PriceAdjustmentLineDto;
import com.boxy.boxy.modules.sales.dto.PriceAdjustmentScope;
import com.boxy.boxy.modules.sales.dto.QuotationDto;
import com.boxy.boxy.core.pdf.PdfDocumentService;
import com.boxy.boxy.modules.sales.dto.RecordPaymentRequest;
import com.boxy.boxy.modules.sales.entity.CashierSession;
import com.boxy.boxy.modules.sales.entity.Customer;
import com.boxy.boxy.modules.sales.entity.Invoice;
import com.boxy.boxy.modules.sales.entity.InvoiceItem;
import com.boxy.boxy.modules.sales.entity.SalesOrder;
import com.boxy.boxy.modules.sales.repository.CashierSessionRepository;
import com.boxy.boxy.modules.sales.repository.CustomerRepository;
import com.boxy.boxy.modules.sales.repository.InvoiceRepository;
import com.boxy.boxy.modules.sales.repository.PaymentRepository;
import com.boxy.boxy.modules.sales.repository.PriceAdjustmentRepository;
import com.boxy.boxy.modules.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CashierSessionRepository cashierSessionRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SalesOrderRepository salesOrderRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StockLevelRepository stockLevelRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PriceAdjustmentRepository priceAdjustmentRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private PdfDocumentService pdfDocumentService;
    @Mock private AuditLogService auditLogService;
    @Mock private com.boxy.boxy.core.realtime.RealtimeEventPublisher realtimeEvents;

    @InjectMocks
    private SalesService salesService;

    private static final Long COMPANY_ID = 1L;

    @BeforeEach
    void authenticateAsCompanyOne() {
        UserPrincipal principal = UserPrincipal.create(
                1L, COMPANY_ID, "cashier", "cashier@boxy.dev", "hash", "Cashier", 1L, "ACTIVE", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** The cashier has an open register in branch 1 (their own) and 3 (the one `branch()` builds). */
    private CashierSession openRegisterInBranches() {
        CashierSession register = CashierSession.builder().id(9L).status("OPEN")
                .initialCash(new BigDecimal("100")).expectedCash(new BigDecimal("100")).build();
        lenient().when(cashierSessionRepository.findByUserIdAndBranchIdAndStatus(1L, 1L, "OPEN"))
                .thenReturn(Optional.of(register));
        lenient().when(cashierSessionRepository.findByUserIdAndBranchIdAndStatus(1L, 3L, "OPEN"))
                .thenReturn(Optional.of(register));
        return register;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void stubCommon() {
        Company company = Company.builder().id(1L).build();
        lenient().when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        lenient().when(documentSequenceService.nextFolio(1L, DocumentType.PRICE_ADJUSTMENT)).thenReturn("PR-00001");
        lenient().when(priceAdjustmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Product product(long id, String cost, String sellingPrice) {
        return Product.builder()
                .id(id)
                .sku("SKU-" + id)
                .name("Producto " + id)
                .costPrice(new BigDecimal(cost))
                .sellingPrice(new BigDecimal(sellingPrice))
                .build();
    }

    @Test
    void skipsProductsWithNoCostOnlyWhenBasedOnCost() {
        stubCommon();
        Product noCost = product(10L, "0", "50.00");
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(10L, COMPANY_ID)).thenReturn(Optional.of(noCost));

        CreatePriceAdjustmentRequest request = CreatePriceAdjustmentRequest.builder()
                .tariff("increase").unit("percent").amount(new BigDecimal("10"))
                .basedOn("cost").roundingMode("none")
                .productIds(List.of("product-10"))
                .build();

        assertThatThrownBy(() -> salesService.createPriceAdjustment(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void repricesFromSalePriceEvenWithoutCost() {
        stubCommon();
        Product noCost = product(11L, "0", "50.00");
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(11L, COMPANY_ID)).thenReturn(Optional.of(noCost));

        CreatePriceAdjustmentRequest request = CreatePriceAdjustmentRequest.builder()
                .tariff("increase").unit("percent").amount(new BigDecimal("10"))
                .basedOn("sale-price").roundingMode("none")
                .productIds(List.of("product-11"))
                .build();

        PriceAdjustmentDto result = salesService.createPriceAdjustment(request);

        assertThat(result.getLines()).hasSize(1);
        assertThat(result.getLines().get(0).getNewSalePrice()).isEqualByComparingTo("55.0000");
        assertThat(noCost.getSellingPrice()).isEqualByComparingTo("55.0000");
    }

    @Test
    void anOverrideWinsOverTheFormulaForThatProductOnly() {
        stubCommon();
        Product a = product(20L, "40.00", "50.00");
        Product b = product(21L, "40.00", "50.00");
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(20L, COMPANY_ID)).thenReturn(Optional.of(a));
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(21L, COMPANY_ID)).thenReturn(Optional.of(b));

        CreatePriceAdjustmentRequest request = CreatePriceAdjustmentRequest.builder()
                .tariff("increase").unit("percent").amount(new BigDecimal("25"))
                .basedOn("cost").roundingMode("none")
                .productIds(List.of("product-20", "product-21"))
                .overrides(Map.of("product-20", new BigDecimal("99.99")))
                .build();

        PriceAdjustmentDto result = salesService.createPriceAdjustment(request);

        var overridden = result.getLines().stream().filter(l -> l.getProductId().equals(20L)).findFirst().orElseThrow();
        var formulaDriven = result.getLines().stream().filter(l -> l.getProductId().equals(21L)).findFirst().orElseThrow();

        assertThat(overridden.getNewSalePrice()).isEqualByComparingTo("99.9900");
        assertThat(overridden.isOverridden()).isTrue();
        assertThat(formulaDriven.getNewSalePrice()).isEqualByComparingTo("50.0000");
        assertThat(formulaDriven.isOverridden()).isFalse();
    }

    @Test
    void aScopeTargetsEveryMatchingProductWithoutListingIds_andHonoursTheChosenExistence() {
        stubCommon();
        Product a = product(30L, "10.00", "20.00");
        Product b = product(31L, "10.00", "20.00");
        Product c = product(32L, "10.00", "20.00");
        when(productRepository.searchCatalog(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("out-of-stock"), any()))
                .thenReturn(new PageImpl<>(List.of(a, b, c)));

        CreatePriceAdjustmentRequest request = CreatePriceAdjustmentRequest.builder()
                .tariff("increase").unit("percent").amount(new BigDecimal("10"))
                .basedOn("sale-price").roundingMode("none")
                .scope(PriceAdjustmentScope.builder().existence("out-of-stock").build())
                .excludedIds(List.of("product-31"))
                .build();

        PriceAdjustmentDto result = salesService.createPriceAdjustment(request);

        assertThat(result.getLines()).extracting(PriceAdjustmentLineDto::getProductId).containsExactly(30L, 32L);
        assertThat(a.getSellingPrice()).isEqualByComparingTo("22.0000");
        assertThat(b.getSellingPrice()).isEqualByComparingTo("20.00");
    }

    @Test
    void anAllExistenceScopeAppliesNoStockFilter() {
        stubCommon();
        when(productRepository.searchCatalog(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(new PageImpl<>(List.of(product(40L, "10", "20"))));

        salesService.createPriceAdjustment(CreatePriceAdjustmentRequest.builder()
                .tariff("increase").unit("amount").amount(BigDecimal.ONE)
                .basedOn("sale-price").roundingMode("none")
                .scope(PriceAdjustmentScope.builder().existence("all").build())
                .build());

        verify(productRepository).searchCatalog(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void anAdjustmentWithNeitherProductsNorScopeIsRejected() {
        assertThatThrownBy(() -> salesService.createPriceAdjustment(CreatePriceAdjustmentRequest.builder()
                .tariff("increase").unit("amount").amount(BigDecimal.ONE)
                .basedOn("sale-price").roundingMode("none").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void previewPagesTheLinesButTotalsCoverEveryProductAndNothingIsSaved() {
        List<Product> products = new java.util.ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            products.add(product(50L + i, i == 5 ? "0" : "10", "100.00"));
        }
        when(productRepository.searchCatalog(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(products));

        var preview = salesService.previewPriceAdjustment(CreatePriceAdjustmentRequest.builder()
                .tariff("increase").unit("percent").amount(new BigDecimal("50"))
                .basedOn("cost").roundingMode("none")
                .scope(PriceAdjustmentScope.builder().existence("all").build())
                .build(), org.springframework.data.domain.PageRequest.of(0, 2));

        assertThat(preview.getLines()).hasSize(2);
        assertThat(preview.getTotalCount()).isEqualTo(4);
        assertThat(preview.getSkippedCount()).isEqualTo(1);
        assertThat(preview.getTotalBefore()).isEqualByComparingTo("400.0000");
        assertThat(preview.getTotalAfter()).isEqualByComparingTo("60.0000");
        verify(productRepository, org.mockito.Mockito.never()).save(any());
    }

    /**
     * POS Step 1 reads the catalog one page at a time (a real import runs to thousands of SKUs):
     * the requested page and every filter must reach the repository query, and a blank search or an
     * unknown `existence` value must mean "no filter" rather than a literal match.
     */
    @Test
    void searchCatalogPassesThePageAndFiltersToTheRepository() {
        when(productRepository.searchCatalog(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        Pageable requested = org.springframework.data.domain.PageRequest.of(2, 20);

        salesService.searchCatalog("  ", 3L, 4L, 5L, 6L, "in-stock", requested);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).searchCatalog(
                any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(6L), org.mockito.ArgumentMatchers.eq("in-stock"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().isPaged()).isTrue();
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void searchCatalogIgnoresAnUnknownExistenceValue() {
        when(productRepository.searchCatalog(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        salesService.searchCatalog(null, null, null, null, null, "whatever", org.springframework.data.domain.PageRequest.of(0, 20));

        verify(productRepository).searchCatalog(
                any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void lookupCatalogSkipsTheQueryWhenNoCodeWasGiven() {
        assertThat(salesService.lookupCatalog(java.util.Arrays.asList(" ", null))).isEmpty();

        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.never()).findActiveByCodes(any(), any());
    }

    /**
     * Regression: `toCatalogItemDto` used to replace a real zero `availableStock` with a
     * hard-coded 50.0, so a product genuinely out of stock could never be filtered as such
     * (breaks POS's "Existencia: Inexistentes" bulk-add filter). Also confirms the new
     * category/brand fields (added for that same bulk-add filter) populate from the product's
     * relations.
     */
    @Test
    void catalogItemReportsRealZeroStockAndCategoryBrandFields() {
        Category category = Category.builder().id(5L).name("Herramientas").build();
        Brand brand = Brand.builder().id(7L).name("Bosch").build();
        Product product = Product.builder()
                .id(30L).sku("SKU-30").name("Producto 30")
                .sellingPrice(new BigDecimal("25.00"))
                .category(category).brand(brand)
                .build();
        when(productRepository.findByCompanyIdAndBarcodeAndDeletedAtIsNull(anyLong(), any()))
                .thenReturn(Optional.of(product));
        when(stockLevelRepository.findByProductId(30L)).thenReturn(List.of());

        CatalogItemDto dto = salesService.findCatalogByBarcode("SKU-30").orElseThrow();

        assertThat(dto.getAvailableStock()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getCategoryId()).isEqualTo(5L);
        assertThat(dto.getCategoryName()).isEqualTo("Herramientas");
        assertThat(dto.getBrandId()).isEqualTo(7L);
        assertThat(dto.getBrandName()).isEqualTo("Bosch");
    }

    /**
     * `CatalogItemDto` now carries a per-branch stock breakdown (POS's new "Existencia por
     * sucursal" column), mirroring the Inventory module's own `ProductService.toDto` — same
     * `StockLevelRepository.getBranchStockByProductId` query, same `ProductDto.BranchStockDto`
     * mapping.
     */
    @Test
    void catalogItemPopulatesStockByBranch() {
        Product product = Product.builder().id(31L).sku("SKU-31").name("Producto 31")
                .sellingPrice(new BigDecimal("10.00")).build();
        when(productRepository.findByCompanyIdAndBarcodeAndDeletedAtIsNull(anyLong(), any()))
                .thenReturn(Optional.of(product));
        when(stockLevelRepository.findByProductId(31L)).thenReturn(List.of());
        when(stockLevelRepository.getBranchStockByProductId(31L)).thenReturn(List.<Object[]>of(
                new Object[]{2L, "SUC-02", "Sucursal Norte", new BigDecimal("12")}
        ));

        CatalogItemDto dto = salesService.findCatalogByBarcode("SKU-31").orElseThrow();

        assertThat(dto.getStockByBranch()).hasSize(1);
        ProductDto.BranchStockDto branchStock = dto.getStockByBranch().get(0);
        assertThat(branchStock.getBranchId()).isEqualTo(2L);
        assertThat(branchStock.getBranchCode()).isEqualTo("SUC-02");
        assertThat(branchStock.getBranchName()).isEqualTo("Sucursal Norte");
        assertThat(branchStock.getQuantity()).isEqualByComparingTo("12");
    }

    /**
     * The new ticket-level `discountAmount` on `CreateQuotationRequest` must reduce the
     * persisted total (on top of any per-line discounts, which are already netted into
     * `subtotal`), and be reflected in the persisted `discountAmount` for display — mirroring
     * how `processCheckout` already applies its own ticket-level discount to a Sale.
     */
    @Test
    void createQuotationAppliesTicketLevelDiscountAmount() {
        openRegisterInBranches();
        Company company = Company.builder().id(1L).build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        Branch branch = Branch.builder().id(1L).name("Sucursal Central").company(company).build();
        when(branchRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(branch));
        User user = User.builder().id(1L).build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(documentSequenceService.nextFolio(1L, DocumentType.QUOTATION)).thenReturn("COT-00001");

        Product product = Product.builder().id(40L).sku("SKU-40").name("Producto 40")
                .sellingPrice(new BigDecimal("100.00")).build();
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(40L, COMPANY_ID)).thenReturn(Optional.of(product));
        when(salesOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateQuotationRequest request = CreateQuotationRequest.builder()
                .customerId(1L)
                .validUntil("2026-12-31")
                .notes("")
                .discountAmount(new BigDecimal("15.00"))
                .lines(List.of(CreateQuotationRequest.QuotationLineRequest.builder()
                        .productId(40L).quantity(new BigDecimal("2")).unitPrice(new BigDecimal("100.00"))
                        .lineDiscount(BigDecimal.ZERO)
                        .build()))
                .build();
        Customer customer = Customer.builder().id(1L).name("Cliente de Prueba").build();
        when(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(customer));

        QuotationDto result = salesService.createQuotation(request);

        // Tax-inclusive (business-patterns.md §6): subtotal = 2*100 = 200 (no line discounts,
        // tax already baked into the sale price, not added again); ticket discount = 15;
        // total = subtotal - ticketDiscount = 200 - 15 = 185.
        assertThat(result.getSubtotal()).isEqualByComparingTo("200.00");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("15.00");
        assertThat(result.getTotal()).isEqualByComparingTo("185.00");
    }

    /**
     * Regression: a line's `lineTotal` used to be `(quantity*unitPrice - lineDiscount) + 16% tax`
     * — tax added on top — which never matched `quantity * unitPrice` the way business-patterns.md
     * §6 documents Sales prices ("tax-inclusive... never added on top"). Also, `lineDiscount` on the
     * returned DTO was hard-coded to zero regardless of what was actually applied, so a discounted
     * line looked like an unexplained total mismatch with no visible discount to account for it.
     */
    @Test
    void quotationLineTotalMatchesQuantityTimesUnitPriceMinusDiscount() {
        openRegisterInBranches();
        Company company = Company.builder().id(1L).build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        Branch branch = Branch.builder().id(1L).name("Sucursal Central").company(company).build();
        when(branchRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(branch));
        User user = User.builder().id(1L).build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(documentSequenceService.nextFolio(1L, DocumentType.QUOTATION)).thenReturn("COT-00003");

        Product product = Product.builder().id(42L).sku("SKU-42").name("Producto 42")
                .sellingPrice(new BigDecimal("50.00")).build();
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(42L, COMPANY_ID)).thenReturn(Optional.of(product));
        when(salesOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateQuotationRequest request = CreateQuotationRequest.builder()
                .customerId(1L)
                .validUntil("2026-12-31")
                .notes("")
                .discountAmount(BigDecimal.ZERO)
                .lines(List.of(CreateQuotationRequest.QuotationLineRequest.builder()
                        .productId(42L).quantity(new BigDecimal("3")).unitPrice(new BigDecimal("50.00"))
                        .lineDiscount(new BigDecimal("20.00"))
                        .build()))
                .build();
        Customer customer = Customer.builder().id(1L).name("Cliente de Prueba").build();
        when(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(customer));

        QuotationDto result = salesService.createQuotation(request);

        // quantity*unitPrice - lineDiscount = 3*50 - 20 = 130, with no tax stacked on top.
        var line = result.getLines().get(0);
        assertThat(line.getLineDiscount()).isEqualByComparingTo("20.00");
        assertThat(line.getLineTotal()).isEqualByComparingTo("130.00");
        assertThat(result.getSubtotal()).isEqualByComparingTo("130.00");
        assertThat(result.getTotal()).isEqualByComparingTo("130.00");
    }

    /**
     * Regression: `CreateQuotationRequest.validUntil` was accepted by the endpoint but silently
     * discarded — no column existed on `SalesOrder` to persist it, so `QuotationDto.validUntil`
     * (already declared on the DTO) was never actually populated. Needed for the "Validez: Hasta
     * {date} ({N} días)" field on the printed Nota de Cotización.
     */
    @Test
    void createQuotationPersistsValidUntil() {
        openRegisterInBranches();
        Company company = Company.builder().id(1L).build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        Branch branch = Branch.builder().id(1L).name("Sucursal Central").company(company).build();
        when(branchRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(branch));
        User user = User.builder().id(1L).build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(documentSequenceService.nextFolio(1L, DocumentType.QUOTATION)).thenReturn("COT-00004");

        Product product = Product.builder().id(43L).sku("SKU-43").name("Producto 43")
                .sellingPrice(new BigDecimal("10.00")).build();
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(43L, COMPANY_ID)).thenReturn(Optional.of(product));
        when(salesOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateQuotationRequest request = CreateQuotationRequest.builder()
                .customerId(1L)
                // The real frontend always sends a full ISO-8601 instant, not a bare date.
                .validUntil("2026-07-25T23:59:59.999Z")
                .notes("")
                .discountAmount(BigDecimal.ZERO)
                .lines(List.of(CreateQuotationRequest.QuotationLineRequest.builder()
                        .productId(43L).quantity(BigDecimal.ONE).unitPrice(new BigDecimal("10.00"))
                        .lineDiscount(BigDecimal.ZERO)
                        .build()))
                .build();
        Customer customer = Customer.builder().id(1L).name("Cliente de Prueba").build();
        when(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(customer));

        QuotationDto result = salesService.createQuotation(request);

        assertThat(result.getValidUntil()).isEqualTo(java.time.Instant.parse("2026-07-25T23:59:59.999Z"));
    }

    /**
     * A ticket discount larger than the subtotal must floor the total at zero, mirroring
     * `processCheckout`'s existing floor-at-zero behavior for Sale.
     */
    @Test
    void createQuotationFloorsTotalAtZeroWhenDiscountExceedsSubtotal() {
        openRegisterInBranches();
        Company company = Company.builder().id(1L).build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        Branch branch = Branch.builder().id(1L).name("Sucursal Central").company(company).build();
        when(branchRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(branch));
        User user = User.builder().id(1L).build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(documentSequenceService.nextFolio(1L, DocumentType.QUOTATION)).thenReturn("COT-00002");

        Product product = Product.builder().id(41L).sku("SKU-41").name("Producto 41")
                .sellingPrice(new BigDecimal("10.00")).build();
        when(productRepository.findByIdAndCompanyIdAndDeletedAtIsNull(41L, COMPANY_ID)).thenReturn(Optional.of(product));
        when(salesOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateQuotationRequest request = CreateQuotationRequest.builder()
                .customerId(1L)
                .validUntil("2026-12-31")
                .notes("")
                .discountAmount(new BigDecimal("999.00"))
                .lines(List.of(CreateQuotationRequest.QuotationLineRequest.builder()
                        .productId(41L).quantity(BigDecimal.ONE).unitPrice(new BigDecimal("10.00"))
                        .lineDiscount(BigDecimal.ZERO)
                        .build()))
                .build();
        Customer customer = Customer.builder().id(1L).name("Cliente de Prueba").build();
        when(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID)).thenReturn(Optional.of(customer));

        QuotationDto result = salesService.createQuotation(request);

        assertThat(result.getTotal()).isEqualByComparingTo("0");
    }

    // ---- real-time events (see RealtimeEventPublisher) ----

    private Branch branch() {
        Company company = Company.builder().id(COMPANY_ID).build();
        return Branch.builder().id(3L).company(company).code("BR").name("Sucursal").build();
    }

    private Warehouse warehouseIn(Branch branch) {
        return Warehouse.builder().id(7L).branch(branch).code("WH").name("Almacén").build();
    }

    private Invoice invoiceWithOneLine(String status, Warehouse warehouse, Product product, String quantity) {
        Invoice invoice = Invoice.builder()
                .id(50L).company(warehouse.getBranch().getCompany()).branch(warehouse.getBranch())
                .warehouse(warehouse).customer(Customer.builder().id(1L).name("Cliente").build())
                .series("B001").number("00042").status(status).documentType("TICKET")
                .totalAmount(new BigDecimal("100")).build();
        invoice.getItems().add(InvoiceItem.builder()
                .id(1L).invoice(invoice).product(product).productName(product.getName()).sku(product.getSku())
                .quantity(new BigDecimal(quantity)).unitPrice(BigDecimal.TEN).unitCost(BigDecimal.ONE)
                .discountAmount(BigDecimal.ZERO).taxAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("100"))
                .build());
        return invoice;
    }

    @Test
    void voidSalePublishesTheRestoredStockAndAVoidedEvent() {
        openRegisterInBranches();
        Warehouse warehouse = warehouseIn(branch());
        Product product = product(5L, "3.00", "10.00");
        product.setMinStockAlert(new BigDecimal("4"));
        Invoice invoice = invoiceWithOneLine("PAID", warehouse, product, "6");
        StockLevel stock = StockLevel.builder().id(1L).warehouse(warehouse).product(product)
                .quantityAvailable(new BigDecimal("2")).build();
        when(invoiceRepository.findByIdAndCompanyId(50L, COMPANY_ID)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(stockLevelRepository.findByWarehouseIdAndProductIdAndVariantIdIsNull(7L, 5L)).thenReturn(Optional.of(stock));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        salesService.voidSale(50L, "Cliente devolvió el pedido");

        assertThat(invoice.getVoidReason()).isEqualTo("Cliente devolvió el pedido");
        assertThat(invoice.getVoidedAt()).isNotNull();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<StockChange>> changes = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(realtimeEvents).stockChanged(org.mockito.ArgumentMatchers.eq(warehouse), changes.capture());
        StockChange change = changes.getValue().get(0);
        assertThat(change.productId()).isEqualTo(5L);
        assertThat(change.availableBefore()).isEqualByComparingTo("2");
        assertThat(change.availableAfter()).isEqualByComparingTo("8");
        assertThat(change.minStock()).isEqualByComparingTo("4");
        verify(realtimeEvents).sale(SaleEvent.Type.VOIDED, 3L, 50L, "B001-00042");
    }

    @Test
    void voidingAnAlreadyVoidedSalePublishesNothing() {
        Invoice invoice = invoiceWithOneLine("VOIDED", warehouseIn(branch()), product(5L, "3.00", "10.00"), "1");
        when(invoiceRepository.findByIdAndCompanyId(50L, COMPANY_ID)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> salesService.voidSale(50L, "otro intento")).isInstanceOf(BusinessException.class);

        org.mockito.Mockito.verifyNoInteractions(realtimeEvents);
    }

    @Test
    void voidingASaleRequiresAReasonAndChangesNothingWithout() {
        Invoice invoice = invoiceWithOneLine("PAID", warehouseIn(branch()), product(5L, "3.00", "10.00"), "1");
        when(invoiceRepository.findByIdAndCompanyId(50L, COMPANY_ID)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> salesService.voidSale(50L, "   ")).isInstanceOf(BusinessException.class);

        assertThat(invoice.getStatus()).isEqualTo("PAID");
        org.mockito.Mockito.verifyNoInteractions(realtimeEvents);
    }

    @Test
    void recordPaymentPublishesAPaymentEvent() {
        openRegisterInBranches();
        Invoice invoice = invoiceWithOneLine("PAID", warehouseIn(branch()), product(5L, "3.00", "10.00"), "1");
        when(invoiceRepository.findByIdAndCompanyId(50L, COMPANY_ID)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("25"));
        request.setPaymentMethod("cash");

        salesService.recordPayment(50L, request);

        verify(realtimeEvents).sale(SaleEvent.Type.PAYMENT_RECORDED, 3L, 50L, "B001-00042");
    }

    @Test
    void aPaymentAddsItsCashToTheOpenRegister() {
        CashierSession register = openRegisterInBranches();
        Invoice invoice = invoiceWithOneLine("PAID", warehouseIn(branch()), product(5L, "3.00", "10.00"), "1");
        when(invoiceRepository.findByIdAndCompanyId(50L, COMPANY_ID)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("25"));
        request.setPaymentMethod("cash");

        salesService.recordPayment(50L, request);

        assertThat(register.getExpectedCash()).isEqualByComparingTo("125");
    }

    @Test
    void withoutAnOpenRegisterNothingCanBeCollectedOrQuoted() {
        Invoice invoice = invoiceWithOneLine("PAID", warehouseIn(branch()), product(5L, "3.00", "10.00"), "1");
        when(invoiceRepository.findByIdAndCompanyId(50L, COMPANY_ID)).thenReturn(Optional.of(invoice));
        when(cashierSessionRepository.findByUserIdAndBranchIdAndStatus(any(), any(), org.mockito.ArgumentMatchers.eq("OPEN")))
                .thenReturn(Optional.empty());
        RecordPaymentRequest payment = new RecordPaymentRequest();
        payment.setAmount(BigDecimal.TEN);
        payment.setPaymentMethod("cash");

        assertThatThrownBy(() -> salesService.recordPayment(50L, payment))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("CASH_REGISTER_CLOSED");

        Company company = Company.builder().id(1L).build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(branchRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(Branch.builder().id(1L).name("Central").company(company).build()));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        CreateQuotationRequest quote = CreateQuotationRequest.builder()
                .customerId(1L).discountAmount(BigDecimal.ZERO).notes("").lines(List.of()).build();
        when(customerRepository.findByIdAndCompanyIdAndDeletedAtIsNull(1L, COMPANY_ID))
                .thenReturn(Optional.of(Customer.builder().id(1L).name("C").build()));

        assertThatThrownBy(() -> salesService.createQuotation(quote))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("CASH_REGISTER_CLOSED");
    }

    @Test
    void voidingASaleTakesItsCashOutOfTheOpenRegister() {
        Warehouse warehouse = warehouseIn(branch());
        Product product = product(5L, "3.00", "10.00");
        Invoice invoice = invoiceWithOneLine("PAID", warehouse, product, "1");
        CashierSession register = CashierSession.builder().id(9L).status("OPEN")
                .initialCash(new BigDecimal("100")).expectedCash(new BigDecimal("160")).build();
        invoice.setCashierSession(register);
        invoice.getPayments().add(com.boxy.boxy.modules.sales.entity.Payment.builder()
                .paymentMethod("CASH").amount(new BigDecimal("60")).status("CONFIRMED").build());
        when(invoiceRepository.findByIdAndCompanyId(50L, COMPANY_ID)).thenReturn(Optional.of(invoice));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(stockLevelRepository.findByWarehouseIdAndProductIdAndVariantIdIsNull(7L, 5L))
                .thenReturn(Optional.of(StockLevel.builder().id(1L).warehouse(warehouse).product(product)
                        .quantityAvailable(new BigDecimal("2")).build()));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        salesService.voidSale(50L, "Error de digitación");

        assertThat(register.getExpectedCash()).isEqualByComparingTo("100");
    }

    @Test
    void aPaymentOnAVoidedSalePublishesNothing() {
        Invoice invoice = invoiceWithOneLine("VOIDED", warehouseIn(branch()), product(5L, "3.00", "10.00"), "1");
        when(invoiceRepository.findByIdAndCompanyId(50L, COMPANY_ID)).thenReturn(Optional.of(invoice));
        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(BigDecimal.TEN);
        request.setPaymentMethod("cash");

        assertThatThrownBy(() -> salesService.recordPayment(50L, request)).isInstanceOf(BusinessException.class);

        org.mockito.Mockito.verifyNoInteractions(realtimeEvents);
    }
}
