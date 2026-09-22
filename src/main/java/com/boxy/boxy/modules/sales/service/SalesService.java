package com.boxy.boxy.modules.sales.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.InsufficientStockException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.pdf.AmountInWordsEs;
import com.boxy.boxy.core.pdf.PdfDocumentService;
import com.boxy.boxy.core.pdf.PdfLineItem;
import com.boxy.boxy.core.security.SecurityUtils;
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
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.entity.StockLevel;
import com.boxy.boxy.modules.inventory.entity.StockMovement;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import com.boxy.boxy.modules.sales.dto.*;
import com.boxy.boxy.modules.sales.entity.*;
import com.boxy.boxy.modules.sales.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
    import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SalesService {

    private final CustomerRepository customerRepository;
    private final CashierSessionRepository cashierSessionRepository;
    private final InvoiceRepository invoiceRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final BranchRepository branchRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PriceAdjustmentRepository priceAdjustmentRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentSequenceService documentSequenceService;
    private final PdfDocumentService pdfDocumentService;

    /** Bolivia has one fixed offset (UTC-4, no DST) — same zone used for every generated PDF's dates. */
    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.of("America/La_Paz"));
    private static final Map<String, String> PAYMENT_METHOD_ES = Map.of(
            "cash", "Efectivo", "card", "Tarjeta", "transfer", "Transferencia", "credit", "Crédito");

    @Transactional(readOnly = true)
    public List<CustomerDto> getAllCustomers() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return customerRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toCustomerDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CustomerDto> getCustomersPaged(String search, String status, Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Boolean isActive = "active".equalsIgnoreCase(status) ? Boolean.TRUE
                : "inactive".equalsIgnoreCase(status) ? Boolean.FALSE
                : null;
        String term = (search != null && !search.isBlank()) ? search.trim() : null;
        return customerRepository.findAllFiltered(companyId, term, isActive, pageable)
                .map(this::toCustomerDto);
    }

    @Transactional(readOnly = true)
    public CustomerDto getCustomerById(Long id) {
        Customer c = customerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        return toCustomerDto(c);
    }

    @Transactional
    public CustomerDto createCustomer(CreateCustomerRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        String docNum = request.getDocumentNumber() != null ? request.getDocumentNumber().trim() : "CLI-" + System.currentTimeMillis();

        if (customerRepository.findByCompanyIdAndDocumentNumberAndDeletedAtIsNull(companyId, docNum).isPresent()) {
            throw new BusinessException("CUSTOMER_EXISTS", "A customer with document number '" + docNum + "' already exists.");
        }

        Customer customer = Customer.builder()
                .company(company)
                .documentType(request.getDocumentType() != null ? request.getDocumentType() : "RFC")
                .documentNumber(docNum)
                .name(request.getName().trim())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .creditLimit(request.getCreditLimit() != null ? request.getCreditLimit() : BigDecimal.ZERO)
                .currentCredit(BigDecimal.ZERO)
                .isActive(true)
                .build();

        return toCustomerDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerDto updateCustomer(Long id, CreateCustomerRequest request) {
        Customer c = customerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));

        if (request.getName() != null) c.setName(request.getName().trim());
        if (request.getDocumentNumber() != null) c.setDocumentNumber(request.getDocumentNumber().trim());
        if (request.getEmail() != null) c.setEmail(request.getEmail());
        if (request.getPhone() != null) c.setPhone(request.getPhone());
        if (request.getAddress() != null) c.setAddress(request.getAddress());
        if (request.getCreditLimit() != null) c.setCreditLimit(request.getCreditLimit());

        return toCustomerDto(customerRepository.save(c));
    }

    @Transactional
    public CustomerDto archiveCustomer(Long id) {
        Customer c = customerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        c.setIsActive(false);
        return toCustomerDto(customerRepository.save(c));
    }

    @Transactional(readOnly = true)
    public boolean checkCustomerUnique(String field, String value, Long excludeId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Optional<Customer> existing = customerRepository.findByCompanyIdAndDocumentNumberAndDeletedAtIsNull(companyId, value);
        if (existing.isEmpty()) {
            return true;
        }
        return excludeId != null && existing.get().getId().equals(excludeId);
    }

    @Transactional(readOnly = true)
    public List<CatalogItemDto> searchCatalog(String term) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        // Was capped at 50 — POS loads this once and filters client-side as the cashier types, so
        // any catalog bigger than the page size silently lost products past it (findable only by
        // an exact barcode scan, a separate query). 500 matches the "fetch the whole active set
        // once" precedent used elsewhere (Purchasing, Price Adjustments).
        List<Product> products = productRepository.findAllFiltered(companyId, term, null, null, true, PageRequest.of(0, 500)).getContent();
        return products.stream().map(this::toCatalogItemDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CatalogItemDto> findCatalogByBarcode(String barcode) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return productRepository.findByCompanyIdAndBarcodeAndDeletedAtIsNull(companyId, barcode)
                .map(this::toCatalogItemDto);
    }

    private CatalogItemDto toCatalogItemDto(Product p) {
        List<StockLevel> levels = stockLevelRepository.findByProductId(p.getId());
        // Real computed value, including a genuine zero — a product truly out of stock must be
        // reportable as such (the old 50.0 fallback here made "Existencia: Inexistentes" filters
        // impossible to ever match anything).
        BigDecimal available = levels.stream()
                .map(s -> s.getQuantityAvailable() != null ? s.getQuantityAvailable() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<com.boxy.boxy.modules.catalog.dto.ProductDto.BranchStockDto> stockByBranch =
                stockLevelRepository.getBranchStockByProductId(p.getId()).stream()
                        .map(row -> com.boxy.boxy.modules.catalog.dto.ProductDto.BranchStockDto.builder()
                                .branchId(row[0] != null ? ((Number) row[0]).longValue() : null)
                                .branchCode((String) row[1])
                                .branchName((String) row[2])
                                .quantity(row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO)
                                .build())
                        .toList();
        return CatalogItemDto.builder()
                .productId(p.getId())
                .sku(p.getSku())
                .barcode(p.getBarcode() != null ? p.getBarcode() : p.getSku())
                .name(p.getName())
                .salePrice(p.getSalePrice() != null ? p.getSalePrice() : (p.getSellingPrice() != null ? p.getSellingPrice() : BigDecimal.ZERO))
                .availableStock(available)
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .brandId(p.getBrand() != null ? p.getBrand().getId() : null)
                .brandName(p.getBrand() != null ? p.getBrand().getName() : "—")
                .unitName(p.getUnitOfMeasure() != null ? p.getUnitOfMeasure().getName() : "—")
                .imageUrl(p.getImageUrl())
                .stockByBranch(stockByBranch)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<QuotationDto> getQuotations(Pageable pageable) {
        return salesOrderRepository.findByOrderTypeOrderByCreatedAtDesc("QUOTATION", pageable)
                .map(this::toQuotationDto);
    }

    @Transactional(readOnly = true)
    public QuotationDto getQuotationById(Long id) {
        SalesOrder so = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", id));
        return toQuotationDto(so);
    }

    @Transactional
    public QuotationDto createQuotation(CreateQuotationRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        Long branchId = request.getBranchId() != null ? request.getBranchId() : 1L;
        Branch branch = branchRepository.findByIdAndDeletedAtIsNull(branchId)
                .orElseGet(() -> branchRepository.findAll().stream().findFirst().orElseThrow());

        Customer customer = (request.getCustomerId() != null
                ? customerRepository.findByIdAndDeletedAtIsNull(request.getCustomerId())
                : Optional.<Customer>empty())
                .orElseGet(() -> getOrCreateDefaultCustomer(company));

        User user = userRepository.findByIdAndDeletedAtIsNull(SecurityUtils.getCurrentUserId())
                .orElseGet(() -> userRepository.findAll().stream().findFirst().orElseThrow());

        String orderNumber = documentSequenceService.nextFolio(companyId, DocumentType.QUOTATION);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;

        SalesOrder so = SalesOrder.builder()
                .company(company)
                .branch(branch)
                .customer(customer)
                .orderNumber(orderNumber)
                .orderType("QUOTATION")
                .status("draft")
                .notes(request.getNotes())
                .createdBy(user)
                .validUntil(parseValidUntil(request.getValidUntil()))
                .build();

        if (request.getLines() != null) {
            for (var line : request.getLines()) {
                Product product = productRepository.findByIdAndDeletedAtIsNull(line.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", line.getProductId()));
                BigDecimal unitPrice = line.getUnitPrice() != null ? line.getUnitPrice() : (product.getSalePrice() != null ? product.getSalePrice() : BigDecimal.ZERO);
                BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE;
                BigDecimal lineDisc = line.getLineDiscount() != null ? line.getLineDiscount() : BigDecimal.ZERO;
                // Tax-inclusive (business-patterns.md §6: "Prices are tax-inclusive... tax is
                // extracted from that total for reporting, never added on top" — Sales-only,
                // Quotations included). The sale price already has tax baked in, so the line
                // total IS quantity × unitPrice − lineDiscount; `lineTax` is only extracted from
                // it afterward for the `taxAmount` reporting figure, mirroring the frontend's own
                // `computeTotals` (`taxMode: 'inclusive'`): `taxAmount = total − total / (1 + taxRate)`.
                BigDecimal lineGross = qty.multiply(unitPrice).subtract(lineDisc);
                BigDecimal taxRate = BigDecimal.valueOf(0.16);
                BigDecimal lineTax = lineGross.subtract(
                        lineGross.divide(BigDecimal.ONE.add(taxRate), 6, java.math.RoundingMode.HALF_UP));

                subtotal = subtotal.add(lineGross);
                taxTotal = taxTotal.add(lineTax);
                discountTotal = discountTotal.add(lineDisc);

                SalesOrderItem item = SalesOrderItem.builder()
                        .salesOrder(so)
                        .product(product)
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .discountRate(BigDecimal.ZERO)
                        .discountAmount(lineDisc)
                        .taxRate(taxRate)
                        .totalAmount(lineGross)
                        .build();
                so.getItems().add(item);
            }
        }

        // Ticket-level discount, on top of the per-line discounts already netted into
        // `subtotal` above via `lineGross`. Kept separate from `subtotal`'s existing
        // net-of-line-discounts meaning — only `discountAmount`/`totalAmount` absorb it,
        // mirroring how `processCheckout` applies its own ticket-level discount.
        BigDecimal ticketDiscount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        discountTotal = discountTotal.add(ticketDiscount);

        so.setSubtotal(subtotal);
        so.setDiscountAmount(discountTotal);
        so.setTaxAmount(taxTotal);
        // `subtotal` (the sum of `lineGross`) already has tax baked in — tax-inclusive, not added
        // again here. Only the ticket-level discount reduces it further.
        so.setTotalAmount(subtotal.subtract(ticketDiscount).max(BigDecimal.ZERO));

        return toQuotationDto(salesOrderRepository.save(so));
    }

    @Transactional
    public QuotationDto updateQuotation(Long id, CreateQuotationRequest request) {
        SalesOrder so = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", id));
        if (request.getNotes() != null) so.setNotes(request.getNotes());
        if (request.getValidUntil() != null) so.setValidUntil(parseValidUntil(request.getValidUntil()));
        return toQuotationDto(salesOrderRepository.save(so));
    }

    @Transactional
    public QuotationDto sendQuotation(Long id) {
        SalesOrder so = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", id));
        so.setStatus("sent");
        return toQuotationDto(salesOrderRepository.save(so));
    }

    @Transactional
    public QuotationDto cancelQuotation(Long id) {
        SalesOrder so = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", id));
        so.setStatus("cancelled");
        return toQuotationDto(salesOrderRepository.save(so));
    }

    @Transactional
    public QuotationDto convertQuotation(Long id, String status) {
        SalesOrder so = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", id));
        so.setStatus(status != null ? status : "converted");
        return toQuotationDto(salesOrderRepository.save(so));
    }

    private QuotationDto toQuotationDto(SalesOrder so) {
        List<QuotationDto.QuotationLineDto> lines = so.getItems().stream()
                .map(i -> QuotationDto.QuotationLineDto.builder()
                        .productId(i.getProduct().getId())
                        .sku(i.getProduct().getSku())
                        .productName(i.getProduct().getName())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .lineDiscount(i.getDiscountAmount())
                        .lineTotal(i.getTotalAmount())
                        .build())
                .toList();

        Invoice resultingInvoice = invoiceRepository.findFirstBySalesOrderId(so.getId()).orElse(null);

        return QuotationDto.builder()
                .id(so.getId())
                .folio(so.getOrderNumber())
                .customerId(so.getCustomer() != null ? so.getCustomer().getId() : 1L)
                .customerName(so.getCustomer() != null ? so.getCustomer().getName() : "Público General")
                .branchId(so.getBranch() != null ? so.getBranch().getId() : 1L)
                .branchName(so.getBranch() != null ? so.getBranch().getName() : "Sucursal Central")
                .status(so.getStatus().toLowerCase())
                .subtotal(so.getSubtotal())
                .discountAmount(so.getDiscountAmount())
                .taxAmount(so.getTaxAmount())
                .total(so.getTotalAmount())
                .lineCount(so.getItems().size())
                .notes(so.getNotes())
                .validUntil(so.getValidUntil())
                .lines(lines)
                .createdAt(so.getCreatedAt())
                .saleId(resultingInvoice != null ? resultingInvoice.getId() : null)
                .saleFolio(resultingInvoice != null ? invoiceFolio(resultingInvoice) : null)
                .build();
    }

    /** `CreateQuotationRequest.validUntil` arrives as an ISO-8601 instant string (the frontend
     *  always sends one, e.g. `"2026-07-25T23:59:59.999Z"`) — tolerant of null/blank/malformed
     *  input rather than failing the whole create/update, since it's a display-only field. */
    private static Instant parseValidUntil(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Optional<CashierSessionDto> getActiveSession(Long branchId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return cashierSessionRepository.findByUserIdAndBranchIdAndStatus(userId, branchId, "OPEN")
                .map(this::toSessionDto);
    }

    @Transactional
    public CashierSessionDto openSession(OpenSessionRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (cashierSessionRepository.findByUserIdAndBranchIdAndStatus(userId, request.getBranchId(), "OPEN").isPresent()) {
            throw new BusinessException("SESSION_ALREADY_OPEN", "You already have an active shift session in this branch.");
        }

        Branch branch = branchRepository.findByIdAndDeletedAtIsNull(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", request.getBranchId()));

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User not found"));

        CashierSession session = CashierSession.builder()
                .branch(branch)
                .user(user)
                .initialCash(request.getInitialCash())
                .expectedCash(request.getInitialCash())
                .status("OPEN")
                .notes(request.getNotes())
                .build();

        return toSessionDto(cashierSessionRepository.save(session));
    }

    @Transactional
    public CashierSessionDto closeSession(Long sessionId, CloseSessionRequest request) {
        CashierSession session = cashierSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CashierSession", sessionId));

        if (!"OPEN".equals(session.getStatus())) {
            throw new BusinessException("SESSION_NOT_OPEN", "Session is already closed.");
        }

        session.setClosedAt(Instant.now());
        session.setActualCash(request.getActualCash());
        session.setDifference(request.getActualCash().subtract(session.getExpectedCash()));
        session.setStatus("CLOSED");
        if (request.getNotes() != null) {
            session.setNotes((session.getNotes() != null ? session.getNotes() + " | " : "") + request.getNotes());
        }

        return toSessionDto(cashierSessionRepository.save(session));
    }

    @Transactional
    public InvoiceDto processCheckout(CheckoutRequest request) {
        // Idempotency check
        if (request.getIdempotencyKey() != null) {
            Optional<Invoice> existing = invoiceRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                return toInvoiceDto(existing.get());
            }
        }

        String saleMethod = request.getPaymentMethod() != null ? request.getPaymentMethod().trim().toLowerCase() : "cash";
        boolean isCredit = "credit".equals(saleMethod);

        if (isCredit && request.getCustomerId() == null) {
            throw new BusinessException("CREDIT_SALE_REQUIRES_CUSTOMER", "A credit sale requires a customer.", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (isCredit && (request.getCreditTermDays() == null || request.getCreditTermDays() <= 0)) {
            throw new BusinessException("CREDIT_SALE_REQUIRES_TERM", "A credit sale requires a credit term.", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Long branchId = request.getBranchId() != null ? request.getBranchId() : 1L;
        Long warehouseId = request.getWarehouseId() != null ? request.getWarehouseId() : 1L;
        Long customerId = request.getCustomerId() != null ? request.getCustomerId() : 1L;

        Long userId = SecurityUtils.getCurrentUserId();
        CashierSession session = cashierSessionRepository.findByUserIdAndBranchIdAndStatus(userId, branchId, "OPEN")
                .orElseGet(() -> {
                    Branch b = branchRepository.findByIdAndDeletedAtIsNull(branchId)
                            .orElseGet(() -> branchRepository.findAll().stream().findFirst().orElseThrow());
                    User u = userRepository.findByIdAndDeletedAtIsNull(userId)
                            .orElseGet(() -> userRepository.findAll().stream().findFirst().orElseThrow());
                    CashierSession newSession = CashierSession.builder()
                            .branch(b)
                            .user(u)
                            .initialCash(BigDecimal.valueOf(1000))
                            .expectedCash(BigDecimal.valueOf(1000))
                            .status("OPEN")
                            .notes("Auto-opened shift for POS")
                            .build();
                    return cashierSessionRepository.save(newSession);
                });

        Branch branch = branchRepository.findByIdAndDeletedAtIsNull(branchId)
                .orElseGet(() -> branchRepository.findAll().stream().findFirst().orElseThrow());

        Company company = branch.getCompany();

        Warehouse warehouse = warehouseRepository.findByIdAndDeletedAtIsNull(warehouseId)
                .orElseGet(() -> warehouseRepository.findAll().stream().findFirst().orElseThrow());

        Customer customer = (customerId != null
                ? customerRepository.findByIdAndDeletedAtIsNull(customerId)
                : Optional.<Customer>empty())
                .orElseGet(() -> getOrCreateDefaultCustomer(company));

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseGet(() -> userRepository.findAll().stream().findFirst().orElseThrow());

        String series = "B001";
        // Correlative per company (not System.currentTimeMillis()), so the visible folio
        // (series + "-" + number) increases one-by-one instead of jumping by however many
        // milliseconds elapsed since the previous sale.
        String number = String.format("%05d", documentSequenceService.next(company.getId(), DocumentType.SALE));

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

            // Links the resulting invoice back to the quotation it was converted from
        // (Ventas' "Ver Nota de Venta" column needs this to resolve the other way).
        SalesOrder sourceQuotation = request.getQuotationId() != null
                ? salesOrderRepository.findById(request.getQuotationId()).orElse(null)
                : null;

        Invoice invoice = Invoice.builder()
                .company(company)
                .branch(branch)
                .warehouse(warehouse)
                .cashierSession(session)
                .customer(customer)
                .salesOrder(sourceQuotation)
                .documentType(request.getDocumentType() != null ? request.getDocumentType().toUpperCase() : "TICKET")
                .series(series)
                .number(number)
                .idempotencyKey(request.getIdempotencyKey())
                .status("PAID")
                .paymentMethod(saleMethod)
                .creditTermDays(isCredit ? request.getCreditTermDays() : null)
                .dueDate(isCredit ? Instant.now().plus(request.getCreditTermDays(), java.time.temporal.ChronoUnit.DAYS) : null)
                .createdBy(user)
                .build();

        if (request.getItems() != null) {
            for (var itemReq : request.getItems()) {
                Product product = productRepository.findByIdAndDeletedAtIsNull(itemReq.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

                BigDecimal unitPrice = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice()
                        : (product.getSalePrice() != null ? product.getSalePrice() : (product.getSellingPrice() != null ? product.getSellingPrice() : BigDecimal.ZERO));

                BigDecimal qty = itemReq.getQuantity() != null ? itemReq.getQuantity() : BigDecimal.ONE;

                // Concurrency safe row-locking: SELECT ... FOR UPDATE
                StockLevel stock = stockLevelRepository.findForUpdate(warehouse.getId(), product.getId())
                        .orElseGet(() -> {
                            StockLevel newStock = StockLevel.builder()
                                    .warehouse(warehouse)
                                    .product(product)
                                    .quantityAvailable(BigDecimal.valueOf(100.0))
                                    .quantityReserved(BigDecimal.ZERO)
                                    .quantityInTransit(BigDecimal.ZERO)
                                    .build();
                            return stockLevelRepository.save(newStock);
                        });

                // Deduct stock
                BigDecimal currentStock = stock.getQuantityAvailable() != null ? stock.getQuantityAvailable() : BigDecimal.ZERO;
                stock.setQuantityAvailable(currentStock.subtract(qty));
                stockLevelRepository.save(stock);

                BigDecimal lineGross = qty.multiply(unitPrice);
                BigDecimal lineDiscount = itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO;
                BigDecimal lineNet = lineGross.subtract(lineDiscount);
                BigDecimal taxRate = itemReq.getTaxRate() != null ? itemReq.getTaxRate() : BigDecimal.valueOf(0.16);
                BigDecimal lineTax = lineNet.multiply(taxRate);
                BigDecimal lineTotal = lineNet.add(lineTax);

                subtotal = subtotal.add(lineNet);
                taxTotal = taxTotal.add(lineTax);

                InvoiceItem item = InvoiceItem.builder()
                        .invoice(invoice)
                        .product(product)
                        .productName(product.getName())
                        .sku(product.getSku())
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .unitCost(product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO)
                        .discountAmount(lineDiscount)
                        .taxAmount(lineTax)
                        .totalAmount(lineTotal)
                        .build();

                invoice.getItems().add(item);

                // Record Kardex movement
                StockMovement movement = StockMovement.builder()
                        .warehouse(warehouse)
                        .product(product)
                        .movementType("SALE_OUT")
                        .quantity(qty.negate())
                        .unitCost(product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO)
                        .balanceAfter(stock.getQuantityAvailable())
                        .referenceType("INVOICE")
                        .referenceId(series + "-" + number)
                        .notes("POS sale to " + customer.getName())
                        .createdBy(user)
                        .build();
                stockMovementRepository.save(movement);
            }
        }

        invoice.setSubtotal(subtotal);
        invoice.setDiscountAmount(discountTotal);
        invoice.setTaxAmount(taxTotal);
        BigDecimal grandTotal = subtotal.add(taxTotal).subtract(discountTotal);
        invoice.setTotalAmount(grandTotal.compareTo(BigDecimal.ZERO) > 0 ? grandTotal : BigDecimal.ZERO);

        // Add Payments
        BigDecimal cashPaid = BigDecimal.ZERO;
        if (request.getPayments() != null && !request.getPayments().isEmpty()) {
            for (var payReq : request.getPayments()) {
                Payment payment = Payment.builder()
                        .invoice(invoice)
                        .paymentMethod(payReq.getPaymentMethod().toUpperCase())
                        .amount(payReq.getAmount())
                        .referenceCode(payReq.getReferenceCode())
                        .status("CONFIRMED")
                        .build();
                invoice.getPayments().add(payment);

                if ("CASH".equalsIgnoreCase(payReq.getPaymentMethod())) {
                    cashPaid = cashPaid.add(payReq.getAmount());
                }
            }
        } else if (!isCredit) {
            // A credit sale gets no settlement payment at checkout — the whole
            // total stays as `balanceDue` until "Registrar Pago a Cuenta" is used.
            String method = request.getPaymentMethod() != null ? request.getPaymentMethod().toUpperCase() : "CASH";
            BigDecimal payAmount = request.getAmountTendered() != null && request.getAmountTendered().compareTo(BigDecimal.ZERO) > 0
                    ? request.getAmountTendered() : invoice.getTotalAmount();
            Payment payment = Payment.builder()
                    .invoice(invoice)
                    .paymentMethod(method)
                    .amount(payAmount)
                    .status("CONFIRMED")
                    .build();
            invoice.getPayments().add(payment);
            if ("CASH".equalsIgnoreCase(method)) {
                cashPaid = cashPaid.add(payAmount);
            }
        }

        // Update Session expected cash
        session.setExpectedCash(session.getExpectedCash().add(cashPaid));
        cashierSessionRepository.save(session);

        Invoice saved = invoiceRepository.save(invoice);
        return toInvoiceDto(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceDto getSaleById(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", id));
        return toInvoiceDto(invoice);
    }

    @Transactional(readOnly = true)
    public byte[] generateQuotationPdf(Long id) {
        SalesOrder so = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", id));
        Company company = so.getCompany();
        Customer customer = so.getCustomer();

        List<PdfLineItem> lines = so.getItems().stream()
                .map(item -> PdfLineItem.builder()
                        .sku(item.getProduct().getSku())
                        .description(item.getProduct().getName())
                        .unitName(item.getProduct().getUnit() != null ? item.getProduct().getUnit().getName() : "")
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .discount(BigDecimal.ZERO)
                        .subtotal(item.getTotalAmount())
                        .build())
                .toList();

        Map<String, Object> partyFields = new LinkedHashMap<>();
        if (customer != null) {
            partyFields.put("Razón Social", customer.getName());
            partyFields.put("NIT/CI", customer.getDocumentNumber());
            if (isNotBlank(customer.getAddress())) {
                partyFields.put("Dirección", customer.getAddress());
            }
            if (isNotBlank(customer.getPhone())) {
                partyFields.put("Teléfono", customer.getPhone());
            }
        }

        Map<String, Object> metaFields = new LinkedHashMap<>();
        metaFields.put("Fecha Cotizada", PDF_DATE.format(so.getCreatedAt()));
        if (so.getValidUntil() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(so.getCreatedAt(), so.getValidUntil());
            metaFields.put("Validez", "Hasta " + PDF_DATE.format(so.getValidUntil()) + " (" + days + " días)");
        }

        Map<String, Object> model = new HashMap<>();
        model.put("company", company);
        model.put("docTitle", "COTIZACIÓN");
        model.put("folio", so.getOrderNumber());
        model.put("priceColumnLabel", "Precio");
        model.put("total", so.getTotalAmount());
        model.put("notes", so.getNotes() != null ? so.getNotes() : "");
        model.put("lines", lines);
        model.put("partyFields", partyFields);
        model.put("metaFields", metaFields);
        model.put("amountInWords", AmountInWordsEs.format(so.getTotalAmount(), company.getCurrencySymbol()));
        // Falls back to the company name (not blank) when no specific seller is attached — matches
        // the reference layout's "Cotizador — LatinaTools" signature line for that case.
        model.put("sellerName", so.getCreatedBy() != null ? so.getCreatedBy().getFullName() : company.getTradeName());
        model.put("leftRoleLabel", "Cotizador");
        model.put("rightRoleLabel", "Vendedor");

        return pdfDocumentService.render("money-document", model);
    }

    @Transactional(readOnly = true)
    public byte[] generateSalePdf(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", id));
        Company company = invoice.getCompany();
        Customer customer = invoice.getCustomer();

        List<PdfLineItem> lines = invoice.getItems().stream()
                .map(item -> PdfLineItem.builder()
                        .sku(item.getSku())
                        .description(item.getProductName())
                        .unitName(item.getProduct() != null && item.getProduct().getUnit() != null
                                ? item.getProduct().getUnit().getName() : "")
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .discount(item.getDiscountAmount())
                        .subtotal(item.getTotalAmount())
                        .build())
                .toList();

        Map<String, Object> partyFields = new LinkedHashMap<>();
        if (customer != null) {
            partyFields.put("Razón Social", customer.getName());
            partyFields.put("NIT/CI", customer.getDocumentNumber());
            if (isNotBlank(customer.getAddress())) {
                partyFields.put("Dirección", customer.getAddress());
            }
            if (isNotBlank(customer.getPhone())) {
                partyFields.put("Teléfono", customer.getPhone());
            }
        }

        Map<String, Object> metaFields = new LinkedHashMap<>();
        metaFields.put("Fecha", PDF_DATE.format(invoice.getCreatedAt()));
        metaFields.put("Método de Pago", PAYMENT_METHOD_ES.getOrDefault(invoice.getPaymentMethod(), invoice.getPaymentMethod()));
        if (invoice.getDueDate() != null) {
            metaFields.put("Vence", PDF_DATE.format(invoice.getDueDate()));
        }

        Map<String, Object> model = new HashMap<>();
        model.put("company", company);
        model.put("docTitle", "NOTA DE VENTA");
        model.put("folio", invoice.getSeries() + "-" + invoice.getNumber());
        model.put("priceColumnLabel", "Precio");
        model.put("total", invoice.getTotalAmount());
        model.put("notes", "");
        model.put("lines", lines);
        model.put("partyFields", partyFields);
        model.put("metaFields", metaFields);
        model.put("amountInWords", AmountInWordsEs.format(invoice.getTotalAmount(), company.getCurrencySymbol()));
        model.put("sellerName", invoice.getCreatedBy() != null ? invoice.getCreatedBy().getFullName() : company.getTradeName());
        model.put("leftRoleLabel", "Cajero");
        model.put("rightRoleLabel", "Vendedor");

        return pdfDocumentService.render("money-document", model);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    @Transactional
    public InvoiceDto voidSale(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", id));

        if ("VOIDED".equalsIgnoreCase(invoice.getStatus())) {
            throw new BusinessException("ALREADY_VOIDED", "La venta ya se encuentra anulada.");
        }

        invoice.setStatus("VOIDED");

        // Restore stock for each item in the invoice
        Warehouse warehouse = invoice.getWarehouse();
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User user = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;

        for (InvoiceItem item : invoice.getItems()) {
            if (item.getProduct() != null && warehouse != null) {
                Product product = item.getProduct();
                StockLevel stock = stockLevelRepository
                        .findByWarehouseIdAndProductIdAndVariantIdIsNull(warehouse.getId(), product.getId())
                        .orElseGet(() -> StockLevel.builder()
                                .warehouse(warehouse)
                                .product(product)
                                .quantityAvailable(BigDecimal.ZERO)
                                .quantityReserved(BigDecimal.ZERO)
                                .quantityInTransit(BigDecimal.ZERO)
                                .build());

                BigDecimal restoredQty = stock.getQuantityAvailable().add(item.getQuantity());
                stock.setQuantityAvailable(restoredQty);
                stockLevelRepository.save(stock);

                StockMovement movement = StockMovement.builder()
                        .warehouse(warehouse)
                        .product(product)
                        .movementType("RETURN")
                        .quantity(item.getQuantity())
                        .unitCost(item.getUnitCost())
                        .balanceAfter(restoredQty)
                        .referenceType("SALE_VOID")
                        .referenceId(invoice.getSeries() + "-" + invoice.getNumber())
                        .notes("Anulación de venta " + invoice.getSeries() + "-" + invoice.getNumber())
                        .createdBy(user)
                        .build();
                stockMovementRepository.save(movement);
            }
        }

        Invoice saved = invoiceRepository.save(invoice);
        return toInvoiceDto(saved);
    }

    @Transactional
    public InvoiceDto recordPayment(Long id, Map<String, Object> payload) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", id));

        if ("VOIDED".equalsIgnoreCase(invoice.getStatus())) {
            throw new BusinessException("SALE_VOIDED", "No se pueden registrar pagos en una venta anulada.");
        }

        BigDecimal amount = new BigDecimal(String.valueOf(payload.getOrDefault("amount", 0)));
        String method = String.valueOf(payload.getOrDefault("paymentMethod", "CASH")).toUpperCase();
        String note = String.valueOf(payload.getOrDefault("note", ""));

        Payment payment = Payment.builder()
                .invoice(invoice)
                .paymentMethod(method)
                .amount(amount)
                .referenceCode(note)
                .status("CONFIRMED")
                .build();
        paymentRepository.save(payment);
        invoice.getPayments().add(payment);

        Invoice saved = invoiceRepository.save(invoice);
        return toInvoiceDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceDto> getInvoices(Long branchId, Pageable pageable) {
        if (branchId != null) {
            return invoiceRepository.findByBranchIdOrderByCreatedAtDesc(branchId, pageable).map(this::toInvoiceDto);
        }
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return invoiceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable).map(this::toInvoiceDto);
    }

    private CustomerDto toCustomerDto(Customer c) {
        String code = c.getDocumentNumber() != null ? c.getDocumentNumber() : "CLI-" + c.getId();
        return CustomerDto.builder()
                .id(c.getId())
                .code(code)
                .name(c.getName())
                .taxId(c.getDocumentNumber())
                .type("company")
                .documentType(c.getDocumentType())
                .documentNumber(c.getDocumentNumber())
                .email(c.getEmail())
                .phone(c.getPhone())
                .address(c.getAddress())
                .branchId(1L)
                .branchName("Sucursal Central")
                .status(Boolean.TRUE.equals(c.getIsActive()) ? "active" : "inactive")
                .creditLimit(c.getCreditLimit() != null ? c.getCreditLimit() : BigDecimal.ZERO)
                .currentCredit(c.getCurrentCredit() != null ? c.getCurrentCredit() : BigDecimal.ZERO)
                .quotationCount(0)
                .saleCount(0)
                .totalPurchased(BigDecimal.ZERO)
                .isActive(Boolean.TRUE.equals(c.getIsActive()))
                .createdAt(c.getCreatedAt())
                .build();
    }

    private CashierSessionDto toSessionDto(CashierSession s) {
        return CashierSessionDto.builder()
                .id(s.getId())
                .branchId(s.getBranch().getId())
                .branchName(s.getBranch().getName())
                .userId(s.getUser().getId())
                .userName(s.getUser().getFullName())
                .openedAt(s.getOpenedAt())
                .closedAt(s.getClosedAt())
                .initialCash(s.getInitialCash())
                .expectedCash(s.getExpectedCash())
                .actualCash(s.getActualCash())
                .difference(s.getDifference())
                .status(s.getStatus())
                .notes(s.getNotes())
                .build();
    }

    private InvoiceDto toInvoiceDto(Invoice inv) {
        List<InvoiceItemDto> itemDtos = inv.getItems().stream()
                .map(i -> InvoiceItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .sku(i.getSku())
                        .productName(i.getProductName())
                        .name(i.getProductName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .unitCost(i.getUnitCost())
                        .discountAmount(i.getDiscountAmount())
                        .taxAmount(i.getTaxAmount())
                        .totalAmount(i.getTotalAmount())
                        .lineTotal(i.getTotalAmount())
                        .unitName(i.getProduct().getUnitOfMeasure() != null ? i.getProduct().getUnitOfMeasure().getName() : "—")
                        .build())
                .toList();

        BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal amountPaid = inv.getPayments().stream()
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balanceDue = total.subtract(amountPaid);
        if (balanceDue.compareTo(BigDecimal.ZERO) < 0) balanceDue = BigDecimal.ZERO;

        String saleStatus;
        if ("VOIDED".equalsIgnoreCase(inv.getStatus())) {
            saleStatus = "voided";
        } else if (amountPaid.compareTo(total) >= 0) {
            saleStatus = "paid";
        } else if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            saleStatus = "partial";
        } else {
            saleStatus = "pending";
        }

        List<PaymentDto> payDtos = inv.getPayments().stream()
                .map(p -> PaymentDto.builder()
                        .id(p.getId())
                        .paymentMethod(p.getPaymentMethod() != null ? p.getPaymentMethod().toLowerCase() : "cash")
                        .amount(p.getAmount())
                        .referenceCode(p.getReferenceCode())
                        .note(p.getReferenceCode() != null ? p.getReferenceCode() : "")
                        .recordedBy(inv.getCreatedBy() != null ? inv.getCreatedBy().getFullName() : "Admin")
                        .recordedAt(p.getCreatedAt())
                        .status(p.getStatus())
                        .createdAt(p.getCreatedAt())
                        .build())
                .toList();

        String folio = inv.getSeries() != null && inv.getNumber() != null
                ? inv.getSeries() + "-" + inv.getNumber()
                : "F-" + inv.getId();

        String payMethod = inv.getPaymentMethod() != null ? inv.getPaymentMethod().toLowerCase() : "cash";

        return InvoiceDto.builder()
                .id(inv.getId())
                .folio(folio)
                .branchId(inv.getBranch().getId())
                .branchName(inv.getBranch().getName())
                .warehouseId(inv.getWarehouse().getId())
                .warehouseName(inv.getWarehouse().getName())
                .customerId(inv.getCustomer().getId())
                .customerName(inv.getCustomer().getName())
                .documentType(inv.getDocumentType())
                .series(inv.getSeries())
                .number(inv.getNumber())
                .subtotal(inv.getSubtotal())
                .discountAmount(inv.getDiscountAmount())
                .taxAmount(inv.getTaxAmount())
                .totalAmount(total)
                .total(total)
                .amountPaid(amountPaid)
                .balanceDue(balanceDue)
                .creditTermDays(inv.getCreditTermDays())
                .dueDate(inv.getDueDate())
                .quotationId(inv.getSalesOrder() != null ? inv.getSalesOrder().getId() : null)
                .status(saleStatus)
                .voidedAt("voided".equals(saleStatus) ? inv.getCreatedAt() : null)
                .voidedBy("voided".equals(saleStatus) ? "Admin" : null)
                .paymentMethod(payMethod)
                .amountTendered(total)
                .changeDue(BigDecimal.ZERO)
                .createdByName(inv.getCreatedBy() != null ? inv.getCreatedBy().getFullName() : "Admin")
                .soldBy(inv.getCreatedBy() != null ? inv.getCreatedBy().getFullName() : "Admin")
                .soldAt(inv.getCreatedAt())
                .items(itemDtos)
                .lines(itemDtos)
                .payments(payDtos)
                .createdAt(inv.getCreatedAt())
                .build();
    }

    private Customer getOrCreateDefaultCustomer(Company company) {
        return customerRepository.findByCompanyIdAndDocumentNumberAndDeletedAtIsNull(company.getId(), "XAXX010101000")
                .orElseGet(() -> {
                    Optional<Customer> any = customerRepository.findByCompanyIdAndDeletedAtIsNull(company.getId()).stream().findFirst();
                    if (any.isPresent()) {
                        return any.get();
                    }
                    Customer defaultCustomer = Customer.builder()
                            .company(company)
                            .documentType("RFC")
                            .documentNumber("XAXX010101000")
                            .name("Público General")
                            .email("ventas@boxy.com")
                            .creditLimit(BigDecimal.ZERO)
                            .currentCredit(BigDecimal.ZERO)
                            .isActive(true)
                            .build();
                    return customerRepository.save(defaultCustomer);
                });
    }

    // --- PRICE ADJUSTMENTS ---

    @Transactional(readOnly = true)
    public Page<PriceAdjustmentDto> getPriceAdjustments(String search, String dateFrom, String dateTo, Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        String term = (search != null && !search.isBlank()) ? search.trim() : null;
        Instant from = com.boxy.boxy.core.web.DateFilterParser.parseStart(dateFrom);
        Instant to = com.boxy.boxy.core.web.DateFilterParser.parseEnd(dateTo);
        return priceAdjustmentRepository.findAllFiltered(companyId, term, from, to, pageable)
                .map(this::toPriceAdjustmentDto);
    }

    @Transactional(readOnly = true)
    public PriceAdjustmentDto getPriceAdjustmentById(Long id) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        PriceAdjustment pa = priceAdjustmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("PriceAdjustment", id));
        return toPriceAdjustmentDto(pa);
    }

    @Transactional
    public PriceAdjustmentDto createPriceAdjustment(CreatePriceAdjustmentRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        String appliedBy = SecurityUtils.getCurrentUser()
                .map(u -> u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername())
                .orElse("Admin");

        // Was count()+1: two concurrent requests can read the same count before either inserts,
        // producing duplicate folios. DocumentSequenceService row-locks the counter instead.
        String folio = documentSequenceService.nextFolio(companyId, DocumentType.PRICE_ADJUSTMENT);

        PriceAdjustment pa = PriceAdjustment.builder()
                .company(company)
                .folio(folio)
                .tariff(request.getTariff())
                .unit(request.getUnit())
                .amount(request.getAmount())
                .basedOn(request.getBasedOn())
                .roundingMode(request.getRoundingMode())
                .notes(request.getNotes())
                .appliedBy(appliedBy)
                .appliedAt(Instant.now())
                .build();

        boolean basedOnCost = "cost".equals(request.getBasedOn());
        Map<String, BigDecimal> overrides = request.getOverrides() != null ? request.getOverrides() : Map.of();

        for (String rawId : request.getProductIds()) {
            Long prodId;
            try {
                prodId = Long.parseLong(rawId.replace("product-", "").trim());
            } catch (NumberFormatException ex) {
                continue;
            }

            Product product = productRepository.findByIdAndDeletedAtIsNull(prodId).orElse(null);
            if (product == null) {
                continue;
            }

            BigDecimal cost = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
            BigDecimal prevSalePrice = product.getSellingPrice() != null ? product.getSellingPrice() : BigDecimal.ZERO;

            // Skip only when the formula would actually need a cost to work from — repricing off
            // the current sale price never reads cost, so a product with none is still valid.
            if (basedOnCost && cost.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal basePrice = basedOnCost ? cost : prevSalePrice;
            BigDecimal override = overrides.get(rawId);
            boolean overridden = override != null;
            BigDecimal newSalePrice = overridden
                    ? override.setScale(4, java.math.RoundingMode.HALF_UP)
                    : PriceAdjustmentCalculator.computeAdjustedPrice(
                            basePrice, request.getTariff(), request.getUnit(), request.getAmount(), request.getRoundingMode());
            BigDecimal prevMargin = computeMarginPercent(prevSalePrice, cost);
            BigDecimal newMargin = computeMarginPercent(newSalePrice, cost);

            PriceAdjustmentLine line = PriceAdjustmentLine.builder()
                    .priceAdjustment(pa)
                    .product(product)
                    .sku(product.getSku())
                    .productName(product.getName())
                    .purchasePrice(cost)
                    .previousSalePrice(prevSalePrice)
                    .newSalePrice(newSalePrice)
                    .previousMarginPercent(prevMargin)
                    .newMarginPercent(newMargin)
                    .overridden(overridden)
                    .build();

            pa.getLines().add(line);

            product.setSellingPrice(newSalePrice);
            productRepository.save(product);
        }

        if (pa.getLines().isEmpty()) {
            throw new BusinessException("NO_VALID_PRODUCTS", "Ninguno de los productos seleccionados tiene un costo válido para recalcular el precio.");
        }

        PriceAdjustment saved = priceAdjustmentRepository.save(pa);
        return toPriceAdjustmentDto(saved);
    }

    private BigDecimal computeMarginPercent(BigDecimal salePrice, BigDecimal cost) {
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0 || cost == null) {
            return null;
        }
        BigDecimal diff = salePrice.subtract(cost);
        return diff.divide(salePrice, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private PriceAdjustmentDto toPriceAdjustmentDto(PriceAdjustment pa) {
        List<PriceAdjustmentLineDto> lineDtos = pa.getLines().stream()
                .map(l -> PriceAdjustmentLineDto.builder()
                        .id(l.getId())
                        .productId(l.getProduct() != null ? l.getProduct().getId() : null)
                        .sku(l.getSku())
                        .productName(l.getProductName())
                        .purchasePrice(l.getPurchasePrice())
                        .previousSalePrice(l.getPreviousSalePrice())
                        .newSalePrice(l.getNewSalePrice())
                        .previousMarginPercent(l.getPreviousMarginPercent())
                        .newMarginPercent(l.getNewMarginPercent())
                        .overridden(Boolean.TRUE.equals(l.getOverridden()))
                        .build())
                .toList();

        BigDecimal totalDelta = BigDecimal.ZERO;
        int deltaCount = 0;
        for (PriceAdjustmentLine l : pa.getLines()) {
            if (l.getPreviousSalePrice() != null && l.getPreviousSalePrice().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff = l.getNewSalePrice().subtract(l.getPreviousSalePrice());
                BigDecimal delta = diff.divide(l.getPreviousSalePrice(), 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                totalDelta = totalDelta.add(delta);
                deltaCount++;
            }
        }
        BigDecimal avgDelta = deltaCount > 0
                ? totalDelta.divide(BigDecimal.valueOf(deltaCount), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return PriceAdjustmentDto.builder()
                .id(pa.getId())
                .folio(pa.getFolio())
                .tariff(pa.getTariff())
                .unit(pa.getUnit())
                .amount(pa.getAmount())
                .basedOn(pa.getBasedOn())
                .roundingMode(pa.getRoundingMode())
                .notes(pa.getNotes())
                .lines(lineDtos)
                .appliedBy(pa.getAppliedBy())
                .appliedAt(pa.getAppliedAt())
                .lineCount(pa.getLines().size())
                .averageDeltaPercent(avgDelta)
                .build();
    }

    // --- SALES DOCUMENTS (UNIFIED LIST & SUMMARY) ---

    @Transactional(readOnly = true)
    public Page<SalesDocumentDto> getSalesDocuments(Long branchId, String kind, String search,
                                                    String paymentMethod, String status,
                                                    String saleStatus, Long customerId,
                                                    String dateFrom, String dateTo,
                                                    Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Instant from = com.boxy.boxy.core.web.DateFilterParser.parseStart(dateFrom);
        Instant to = com.boxy.boxy.core.web.DateFilterParser.parseEnd(dateTo);

        List<SalesDocumentDto> docs = new ArrayList<>();

        boolean includeSales = kind == null || kind.isBlank() || "sale".equalsIgnoreCase(kind);
        boolean includeQuotations = kind == null || kind.isBlank() || "quotation".equalsIgnoreCase(kind);

        // Built once and reused by the quotations loop below to resolve each
        // converted quotation's resulting Invoice ("Ver Nota de Venta").
        List<Invoice> allInvoices = invoiceRepository.findByCompanyId(companyId);
        Map<Long, Invoice> invoiceBySalesOrderId = new HashMap<>();
        for (Invoice inv : allInvoices) {
            if (inv.getSalesOrder() != null) {
                invoiceBySalesOrderId.put(inv.getSalesOrder().getId(), inv);
            }
        }

        if (includeSales) {
            for (Invoice inv : allInvoices) {
                if (branchId != null && inv.getBranch() != null && !branchId.equals(inv.getBranch().getId())) {
                    continue;
                }
                if (customerId != null && inv.getCustomer() != null && !customerId.equals(inv.getCustomer().getId())) {
                    continue;
                }
                // "Venta de Productos" only lists what's genuinely paid — a
                // credit sale still owed on belongs to Cuentas por Cobrar, and a
                // voided sale was never really collected either way.
                if (!isFullyPaid(inv)) {
                    continue;
                }
                docs.add(toSaleDocumentDto(inv));
            }
        }

        if (includeQuotations) {
            List<SalesOrder> orders = salesOrderRepository.findByCompanyId(companyId);
            for (SalesOrder so : orders) {
                if (branchId != null && so.getBranch() != null && !branchId.equals(so.getBranch().getId())) {
                    continue;
                }
                if (customerId != null && so.getCustomer() != null && !customerId.equals(so.getCustomer().getId())) {
                    continue;
                }
                // A quotation converted to a credit sale (`status = "credit"`) belongs
                // exclusively to Cuentas por Cobrar until the resulting Invoice is fully
                // paid — the underlying sale then appears on its own via `isFullyPaid`
                // above, so this quotation row must not also show up in the meantime.
                if ("credit".equalsIgnoreCase(so.getStatus())) {
                    continue;
                }
                docs.add(toQuotationDocumentDto(so, invoiceBySalesOrderId.get(so.getId())));
            }
        }

        // Filter in-memory
        List<SalesDocumentDto> filtered = docs.stream()
                .filter(d -> {
                    if (search != null && !search.isBlank()) {
                        String s = search.trim().toLowerCase();
                        boolean matchesFolio = d.getFolio() != null && d.getFolio().toLowerCase().contains(s);
                        boolean matchesCustomer = d.getCustomerName() != null && d.getCustomerName().toLowerCase().contains(s);
                        if (!matchesFolio && !matchesCustomer) return false;
                    }
                    if (paymentMethod != null && !paymentMethod.isBlank()) {
                        if (d.getPaymentMethod() == null || !d.getPaymentMethod().equalsIgnoreCase(paymentMethod)) {
                            return false;
                        }
                    }
                    if (status != null && !status.isBlank()) {
                        if (d.getQuotationStatus() == null || !d.getQuotationStatus().equalsIgnoreCase(status)) {
                            return false;
                        }
                    }
                    if (saleStatus != null && !saleStatus.isBlank()) {
                        if (d.getSaleStatus() == null || !d.getSaleStatus().equalsIgnoreCase(saleStatus)) {
                            return false;
                        }
                    }
                    if (from != null && (d.getIssuedAt() == null || d.getIssuedAt().isBefore(from))) {
                        return false;
                    }
                    if (to != null && (d.getIssuedAt() == null || d.getIssuedAt().isAfter(to))) {
                        return false;
                    }
                    return true;
                })
                .sorted((a, b) -> {
                    Instant ta = a.getIssuedAt() != null ? a.getIssuedAt() : Instant.MIN;
                    Instant tb = b.getIssuedAt() != null ? b.getIssuedAt() : Instant.MIN;
                    return tb.compareTo(ta);
                })
                .toList();

        int total = filtered.size();
        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int fromIndex = pageNumber * pageSize;
        List<SalesDocumentDto> pagedList;
        if (fromIndex >= total) {
            pagedList = Collections.emptyList();
        } else {
            int toIndex = Math.min(fromIndex + pageSize, total);
            pagedList = filtered.subList(fromIndex, toIndex);
        }

        return new org.springframework.data.domain.PageImpl<>(pagedList, pageable, total);
    }

    @Transactional(readOnly = true)
    public SalesSummaryDto getSalesSummary(Long branchId, Long customerId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();

        List<Invoice> invoices = invoiceRepository.findByCompanyId(companyId);
        BigDecimal collected = BigDecimal.ZERO;

        for (Invoice inv : invoices) {
            if (branchId != null && inv.getBranch() != null && !branchId.equals(inv.getBranch().getId())) {
                continue;
            }
            if (customerId != null && inv.getCustomer() != null && !customerId.equals(inv.getCustomer().getId())) {
                continue;
            }
            if ("VOIDED".equalsIgnoreCase(inv.getStatus())) {
                continue;
            }

            BigDecimal paid = inv.getPayments().stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Money actually collected — what's still owed on an open credit sale
            // belongs to Cuentas por Cobrar's own summary, not this one.
            collected = collected.add(paid);
        }

        List<SalesOrder> orders = salesOrderRepository.findByCompanyId(companyId);
        BigDecimal activeQuotations = BigDecimal.ZERO;
        List<String> activeStatuses = List.of("draft", "pending-approval", "approved", "sent");

        for (SalesOrder so : orders) {
            if (branchId != null && so.getBranch() != null && !branchId.equals(so.getBranch().getId())) {
                continue;
            }
            if (customerId != null && so.getCustomer() != null && !customerId.equals(so.getCustomer().getId())) {
                continue;
            }
            String st = so.getStatus() != null ? so.getStatus().toLowerCase() : "";
            if (activeStatuses.contains(st)) {
                activeQuotations = activeQuotations.add(so.getTotalAmount() != null ? so.getTotalAmount() : BigDecimal.ZERO);
            }
        }

        return SalesSummaryDto.builder()
                .collected(collected)
                .activeQuotations(activeQuotations)
                .build();
    }

    /**
     * Non-reserving preview — same counters {@link #processCheckout}/{@link #createQuotation}
     * actually advance, just peeked rather than incremented (see
     * {@link DocumentSequenceService#peekNextFolio}), so this finally predicts the real next
     * folio instead of drifting from it. Never treat the result as reserved: re-fetch right
     * before checkout if the number must be exact.
     * <p>
     * The "V-" prefix here is this preview's own display convention — the invoice's real folio is
     * {@code series + "-" + number} (e.g. {@code B001-00042}), not {@code V-00042}; only the
     * underlying sequential number is shared between the two.
     */
    @Transactional(readOnly = true)
    public NextFolioPreviewDto getNextFolioPreview() {
        Long companyId = SecurityUtils.getCurrentCompanyId();

        return NextFolioPreviewDto.builder()
                .sale(String.format("V-%05d", documentSequenceService.peekNext(companyId, DocumentType.SALE)))
                .quotation(documentSequenceService.peekNextFolio(companyId, DocumentType.QUOTATION))
                .build();
    }

    private boolean isFullyPaid(Invoice inv) {
        if ("VOIDED".equalsIgnoreCase(inv.getStatus())) {
            return false;
        }
        BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = inv.getPayments().stream()
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return paid.compareTo(total) >= 0;
    }

    private String invoiceFolio(Invoice inv) {
        return inv.getSeries() != null && inv.getNumber() != null
                ? inv.getSeries() + "-" + inv.getNumber()
                : "V-" + String.format("%05d", inv.getId());
    }

    private SalesDocumentDto toSaleDocumentDto(Invoice inv) {
        String folio = invoiceFolio(inv);

        BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = inv.getPayments().stream()
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balanceDue = total.subtract(paid);
        if (balanceDue.compareTo(BigDecimal.ZERO) < 0) balanceDue = BigDecimal.ZERO;

        String saleStatus;
        if ("VOIDED".equalsIgnoreCase(inv.getStatus())) {
            saleStatus = "voided";
        } else if (paid.compareTo(total) >= 0) {
            saleStatus = "paid";
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            saleStatus = "partial";
        } else {
            saleStatus = "pending";
        }

        String paymentMethod = inv.getPaymentMethod() != null ? inv.getPaymentMethod().toLowerCase() : "cash";

        return SalesDocumentDto.builder()
                .id(String.valueOf(inv.getId()))
                .kind("sale")
                .folio(folio)
                .issuedAt(inv.getCreatedAt())
                .customerId(inv.getCustomer() != null ? String.valueOf(inv.getCustomer().getId()) : null)
                .customerName(inv.getCustomer() != null ? inv.getCustomer().getName() : "Cliente General")
                .lineCount(inv.getItems() != null ? inv.getItems().size() : 0)
                .total(total)
                .paymentMethod(paymentMethod)
                .saleStatus(saleStatus)
                .balanceDue(balanceDue)
                .quotationStatus(null)
                .branchId(inv.getBranch() != null ? String.valueOf(inv.getBranch().getId()) : "1")
                .build();
    }

    private SalesDocumentDto toQuotationDocumentDto(SalesOrder so, Invoice resultingInvoice) {
        String folio = so.getOrderNumber() != null ? so.getOrderNumber() : "COT-" + String.format("%05d", so.getId());
        BigDecimal total = so.getTotalAmount() != null ? so.getTotalAmount() : BigDecimal.ZERO;

        return SalesDocumentDto.builder()
                .id(String.valueOf(so.getId()))
                .kind("quotation")
                .folio(folio)
                .issuedAt(so.getCreatedAt())
                .customerId(so.getCustomer() != null ? String.valueOf(so.getCustomer().getId()) : null)
                .customerName(so.getCustomer() != null ? so.getCustomer().getName() : "Cliente General")
                .lineCount(so.getItems() != null ? so.getItems().size() : 0)
                .total(total)
                .paymentMethod(null)
                .saleStatus(null)
                .balanceDue(BigDecimal.ZERO)
                .quotationStatus(so.getStatus() != null ? so.getStatus().toLowerCase() : "draft")
                .branchId(so.getBranch() != null ? String.valueOf(so.getBranch().getId()) : "1")
                .saleId(resultingInvoice != null ? String.valueOf(resultingInvoice.getId()) : null)
                .saleFolio(resultingInvoice != null ? invoiceFolio(resultingInvoice) : null)
                .build();
    }

    // --- ACCOUNTS RECEIVABLE (CUENTAS POR COBRAR) ---

    @Transactional(readOnly = true)
    public Page<CreditSaleListItemDto> getReceivables(String search, String status, String dateFrom, String dateTo,
                                                       String sortField, String sortDirection, Pageable pageable) {
        List<CreditSaleListItemDto> filtered = filterReceivables(search, status, dateFrom, dateTo).stream()
                .sorted(receivablesComparator(sortField, sortDirection))
                .toList();

        int total = filtered.size();
        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int fromIndex = pageNumber * pageSize;
        List<CreditSaleListItemDto> pagedList;
        if (fromIndex >= total) {
            pagedList = Collections.emptyList();
        } else {
            int toIndex = Math.min(fromIndex + pageSize, total);
            pagedList = filtered.subList(fromIndex, toIndex);
        }

        return new org.springframework.data.domain.PageImpl<>(pagedList, pageable, total);
    }

    @Transactional(readOnly = true)
    public ReceivablesSummaryDto getReceivablesSummary(String search, String status, String dateFrom, String dateTo) {
        Instant now = Instant.now();
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;

        for (CreditSaleListItemDto d : filterReceivables(search, status, dateFrom, dateTo)) {
            totalOutstanding = totalOutstanding.add(d.getBalanceDue());
            if (d.getBalanceDue().compareTo(BigDecimal.ZERO) > 0 && d.getDueDate() != null && d.getDueDate().isBefore(now)) {
                totalOverdue = totalOverdue.add(d.getBalanceDue());
            }
        }

        return ReceivablesSummaryDto.builder()
                .totalOutstanding(totalOutstanding)
                .totalOverdue(totalOverdue)
                .build();
    }

    private List<CreditSaleListItemDto> filterReceivables(String search, String status, String dateFrom, String dateTo) {
        Long companyId = SecurityUtils.getCurrentCompanyId();

        java.time.Instant from = com.boxy.boxy.core.web.DateFilterParser.parseStart(dateFrom);
        java.time.Instant to = com.boxy.boxy.core.web.DateFilterParser.parseEnd(dateTo);

        return invoiceRepository.findByCompanyId(companyId).stream()
                .filter(inv -> "credit".equalsIgnoreCase(inv.getPaymentMethod()))
                .map(this::toCreditSaleListItemDto)
                // Cuentas por Cobrar only tracks what's still owed — a fully-paid
                // or voided credit sale has nothing left to collect.
                .filter(d -> !"voided".equals(d.getStatus()) && d.getBalanceDue().compareTo(BigDecimal.ZERO) > 0)
                .filter(d -> matchesReceivableSearch(d, search))
                .filter(d -> status == null || status.isBlank() || status.equalsIgnoreCase(d.getStatus()))
                .filter(d -> from == null || (d.getDueDate() != null && !d.getDueDate().isBefore(from)))
                .filter(d -> to == null || (d.getDueDate() != null && !d.getDueDate().isAfter(to)))
                .toList();
    }

    private boolean matchesReceivableSearch(CreditSaleListItemDto d, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.trim().toLowerCase();
        if (d.getCustomerName() != null && d.getCustomerName().toLowerCase().contains(needle)) {
            return true;
        }
        if (d.getFolio() != null && d.getFolio().toLowerCase().contains(needle)) {
            return true;
        }
        return d.getProducts().stream().anyMatch(p ->
                (p.getSku() != null && p.getSku().toLowerCase().contains(needle))
                        || (p.getName() != null && p.getName().toLowerCase().contains(needle)));
    }

    private Comparator<CreditSaleListItemDto> receivablesComparator(String sortField, String sortDirection) {
        String field = (sortField == null || sortField.isBlank()) ? "dueDate" : sortField;
        boolean desc = "desc".equalsIgnoreCase(sortDirection);
        Comparator<CreditSaleListItemDto> cmp = switch (field) {
            case "folio" -> Comparator.comparing(CreditSaleListItemDto::getFolio, Comparator.nullsLast(String::compareTo));
            case "customerName" -> Comparator.comparing(CreditSaleListItemDto::getCustomerName, Comparator.nullsLast(String::compareTo));
            case "soldAt" -> Comparator.comparing(CreditSaleListItemDto::getSoldAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "total" -> Comparator.comparing(CreditSaleListItemDto::getTotal, Comparator.nullsLast(Comparator.naturalOrder()));
            case "balanceDue" -> Comparator.comparing(CreditSaleListItemDto::getBalanceDue, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(CreditSaleListItemDto::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return desc ? cmp.reversed() : cmp;
    }

    private CreditSaleListItemDto toCreditSaleListItemDto(Invoice inv) {
        BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = inv.getPayments().stream()
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balanceDue = total.subtract(paid);
        if (balanceDue.compareTo(BigDecimal.ZERO) < 0) {
            balanceDue = BigDecimal.ZERO;
        }

        String saleStatus;
        if ("VOIDED".equalsIgnoreCase(inv.getStatus())) {
            saleStatus = "voided";
        } else if (paid.compareTo(total) >= 0) {
            saleStatus = "paid";
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            saleStatus = "partial";
        } else {
            saleStatus = "pending";
        }

        String folio = inv.getSeries() != null && inv.getNumber() != null
                ? inv.getSeries() + "-" + inv.getNumber()
                : "V-" + String.format("%05d", inv.getId());

        java.util.LinkedHashMap<String, CreditSaleListItemDto.ProductSummaryDto> products = new java.util.LinkedHashMap<>();
        for (InvoiceItem item : inv.getItems()) {
            products.putIfAbsent(item.getSku(), CreditSaleListItemDto.ProductSummaryDto.builder()
                    .sku(item.getSku())
                    .name(item.getProductName())
                    .build());
        }

        return CreditSaleListItemDto.builder()
                .id(inv.getId())
                .folio(folio)
                .customerId(inv.getCustomer() != null ? inv.getCustomer().getId() : null)
                .customerName(inv.getCustomer() != null ? inv.getCustomer().getName() : "Cliente General")
                .soldAt(inv.getCreatedAt())
                .creditTermDays(inv.getCreditTermDays() != null ? inv.getCreditTermDays() : 0)
                .dueDate(inv.getDueDate() != null ? inv.getDueDate() : inv.getCreatedAt())
                .total(total)
                .amountPaid(paid)
                .balanceDue(balanceDue)
                .status(saleStatus)
                .products(new ArrayList<>(products.values()))
                .branchId(inv.getBranch() != null ? inv.getBranch().getId() : null)
                .build();
    }
}
