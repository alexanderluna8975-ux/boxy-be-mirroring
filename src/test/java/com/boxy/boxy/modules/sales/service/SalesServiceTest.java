package com.boxy.boxy.modules.sales.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.sequence.DocumentSequenceService;
import com.boxy.boxy.core.sequence.DocumentType;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.repository.BranchRepository;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
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
import com.boxy.boxy.modules.sales.dto.QuotationDto;
import com.boxy.boxy.core.pdf.PdfDocumentService;
import com.boxy.boxy.modules.sales.entity.Customer;
import com.boxy.boxy.modules.sales.entity.SalesOrder;
import com.boxy.boxy.modules.sales.repository.CashierSessionRepository;
import com.boxy.boxy.modules.sales.repository.CustomerRepository;
import com.boxy.boxy.modules.sales.repository.InvoiceRepository;
import com.boxy.boxy.modules.sales.repository.PaymentRepository;
import com.boxy.boxy.modules.sales.repository.PriceAdjustmentRepository;
import com.boxy.boxy.modules.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private SalesService salesService;

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
        when(productRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(noCost));

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
        when(productRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(noCost));

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
        when(productRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(a));
        when(productRepository.findByIdAndDeletedAtIsNull(21L)).thenReturn(Optional.of(b));

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

    /**
     * Regression for POS: `searchCatalog` used to hard-code a fixed page size (first 50, later
     * 500), so any catalog bigger than that silently lost items past the page (only reachable by
     * an exact barcode scan) — confirmed to actually bite once a real Excel import pushed the
     * catalog past 500 SKUs. Confirms the repository is now asked for the whole active set,
     * unpaged, instead of guessing a new fixed ceiling.
     */
    @Test
    void searchCatalogRequestsTheWholeCatalogUnpaged() {
        when(productRepository.findAllFiltered(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        salesService.searchCatalog("");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAllFiltered(any(), any(), any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().isPaged()).isFalse();
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
        Company company = Company.builder().id(1L).build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        Branch branch = Branch.builder().id(1L).name("Sucursal Central").build();
        when(branchRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(branch));
        User user = User.builder().id(1L).build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(documentSequenceService.nextFolio(1L, DocumentType.QUOTATION)).thenReturn("COT-00001");

        Product product = Product.builder().id(40L).sku("SKU-40").name("Producto 40")
                .sellingPrice(new BigDecimal("100.00")).build();
        when(productRepository.findByIdAndDeletedAtIsNull(40L)).thenReturn(Optional.of(product));
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
        when(customerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(customer));

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
        Company company = Company.builder().id(1L).build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        Branch branch = Branch.builder().id(1L).name("Sucursal Central").build();
        when(branchRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(branch));
        User user = User.builder().id(1L).build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(documentSequenceService.nextFolio(1L, DocumentType.QUOTATION)).thenReturn("COT-00003");

        Product product = Product.builder().id(42L).sku("SKU-42").name("Producto 42")
                .sellingPrice(new BigDecimal("50.00")).build();
        when(productRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(product));
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
        when(customerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(customer));

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
        Company company = Company.builder().id(1L).build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        Branch branch = Branch.builder().id(1L).name("Sucursal Central").build();
        when(branchRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(branch));
        User user = User.builder().id(1L).build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(documentSequenceService.nextFolio(1L, DocumentType.QUOTATION)).thenReturn("COT-00004");

        Product product = Product.builder().id(43L).sku("SKU-43").name("Producto 43")
                .sellingPrice(new BigDecimal("10.00")).build();
        when(productRepository.findByIdAndDeletedAtIsNull(43L)).thenReturn(Optional.of(product));
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
        when(customerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(customer));

        QuotationDto result = salesService.createQuotation(request);

        assertThat(result.getValidUntil()).isEqualTo(java.time.Instant.parse("2026-07-25T23:59:59.999Z"));
    }

    /**
     * A ticket discount larger than the subtotal must floor the total at zero, mirroring
     * `processCheckout`'s existing floor-at-zero behavior for Sale.
     */
    @Test
    void createQuotationFloorsTotalAtZeroWhenDiscountExceedsSubtotal() {
        Company company = Company.builder().id(1L).build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        Branch branch = Branch.builder().id(1L).name("Sucursal Central").build();
        when(branchRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(branch));
        User user = User.builder().id(1L).build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(documentSequenceService.nextFolio(1L, DocumentType.QUOTATION)).thenReturn("COT-00002");

        Product product = Product.builder().id(41L).sku("SKU-41").name("Producto 41")
                .sellingPrice(new BigDecimal("10.00")).build();
        when(productRepository.findByIdAndDeletedAtIsNull(41L)).thenReturn(Optional.of(product));
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
        when(customerRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(customer));

        QuotationDto result = salesService.createQuotation(request);

        assertThat(result.getTotal()).isEqualByComparingTo("0");
    }
}
