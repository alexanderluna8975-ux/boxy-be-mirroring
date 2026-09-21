package com.boxy.boxy.modules.sales.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.sequence.DocumentSequenceService;
import com.boxy.boxy.core.sequence.DocumentType;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.repository.BranchRepository;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import com.boxy.boxy.modules.sales.dto.CreatePriceAdjustmentRequest;
import com.boxy.boxy.modules.sales.dto.PriceAdjustmentDto;
import com.boxy.boxy.core.pdf.PdfDocumentService;
import com.boxy.boxy.modules.sales.repository.CashierSessionRepository;
import com.boxy.boxy.modules.sales.repository.CustomerRepository;
import com.boxy.boxy.modules.sales.repository.InvoiceRepository;
import com.boxy.boxy.modules.sales.repository.PaymentRepository;
import com.boxy.boxy.modules.sales.repository.PriceAdjustmentRepository;
import com.boxy.boxy.modules.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
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
}
