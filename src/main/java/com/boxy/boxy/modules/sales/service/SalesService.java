package com.boxy.boxy.modules.sales.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.InsufficientStockException;
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
import java.util.ArrayList;
import java.util.Collections;
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

    @Transactional(readOnly = true)
    public List<CustomerDto> getAllCustomers() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return customerRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toCustomerDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CustomerDto> getCustomersPaged(Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return customerRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable)
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
        List<Product> products = productRepository.findAllFiltered(companyId, term, null, null, true, PageRequest.of(0, 50)).getContent();
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
        BigDecimal available = levels.stream()
                .map(s -> s.getQuantityAvailable() != null ? s.getQuantityAvailable() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            available = BigDecimal.valueOf(50.0);
        }
        return CatalogItemDto.builder()
                .productId(p.getId())
                .sku(p.getSku())
                .barcode(p.getBarcode() != null ? p.getBarcode() : p.getSku())
                .name(p.getName())
                .salePrice(p.getSalePrice() != null ? p.getSalePrice() : (p.getSellingPrice() != null ? p.getSellingPrice() : BigDecimal.ZERO))
                .availableStock(available)
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

        String orderNumber = "COT-" + System.currentTimeMillis();

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
                .build();

        if (request.getLines() != null) {
            for (var line : request.getLines()) {
                Product product = productRepository.findByIdAndDeletedAtIsNull(line.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", line.getProductId()));
                BigDecimal unitPrice = line.getUnitPrice() != null ? line.getUnitPrice() : (product.getSalePrice() != null ? product.getSalePrice() : BigDecimal.ZERO);
                BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE;
                BigDecimal lineDisc = line.getLineDiscount() != null ? line.getLineDiscount() : BigDecimal.ZERO;
                BigDecimal lineGross = qty.multiply(unitPrice).subtract(lineDisc);
                BigDecimal lineTax = lineGross.multiply(BigDecimal.valueOf(0.16));
                BigDecimal lineTot = lineGross.add(lineTax);

                subtotal = subtotal.add(lineGross);
                taxTotal = taxTotal.add(lineTax);
                discountTotal = discountTotal.add(lineDisc);

                SalesOrderItem item = SalesOrderItem.builder()
                        .salesOrder(so)
                        .product(product)
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .discountRate(BigDecimal.ZERO)
                        .taxRate(BigDecimal.valueOf(0.16))
                        .totalAmount(lineTot)
                        .build();
                so.getItems().add(item);
            }
        }

        so.setSubtotal(subtotal);
        so.setDiscountAmount(discountTotal);
        so.setTaxAmount(taxTotal);
        so.setTotalAmount(subtotal.add(taxTotal));

        return toQuotationDto(salesOrderRepository.save(so));
    }

    @Transactional
    public QuotationDto updateQuotation(Long id, CreateQuotationRequest request) {
        SalesOrder so = salesOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", id));
        if (request.getNotes() != null) so.setNotes(request.getNotes());
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
                        .lineDiscount(BigDecimal.ZERO)
                        .lineTotal(i.getTotalAmount())
                        .build())
                .toList();

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
                .lines(lines)
                .createdAt(so.getCreatedAt())
                .build();
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
        String number = String.valueOf(System.currentTimeMillis()).substring(3);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        Invoice invoice = Invoice.builder()
                .company(company)
                .branch(branch)
                .warehouse(warehouse)
                .cashierSession(session)
                .customer(customer)
                .documentType(request.getDocumentType() != null ? request.getDocumentType().toUpperCase() : "TICKET")
                .series(series)
                .number(number)
                .idempotencyKey(request.getIdempotencyKey())
                .status("PAID")
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
        } else {
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

        String payMethod = !payDtos.isEmpty() ? payDtos.get(0).getPaymentMethod().toLowerCase() : "cash";

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
    public Page<PriceAdjustmentDto> getPriceAdjustments(Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return priceAdjustmentRepository.findByCompanyIdOrderByAppliedAtDesc(companyId, pageable)
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

        long count = priceAdjustmentRepository.countByCompanyId(companyId) + 1;
        String folio = String.format("PR-%05d", count);

        PriceAdjustment pa = PriceAdjustment.builder()
                .company(company)
                .folio(folio)
                .marginPercent(request.getMarginPercent())
                .notes(request.getNotes())
                .appliedBy(appliedBy)
                .appliedAt(Instant.now())
                .build();

        BigDecimal targetMargin = request.getMarginPercent();

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

            BigDecimal cost = product.getCostPrice() != null && product.getCostPrice().compareTo(BigDecimal.ZERO) > 0
                    ? product.getCostPrice()
                    : BigDecimal.ZERO;

            if (cost.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal prevSalePrice = product.getSellingPrice() != null ? product.getSellingPrice() : BigDecimal.ZERO;
            BigDecimal newSalePrice = computeSalePrice(cost, targetMargin);
            BigDecimal prevMargin = computeMarginPercent(prevSalePrice, cost);

            PriceAdjustmentLine line = PriceAdjustmentLine.builder()
                    .priceAdjustment(pa)
                    .product(product)
                    .sku(product.getSku())
                    .productName(product.getName())
                    .purchasePrice(cost)
                    .previousSalePrice(prevSalePrice)
                    .newSalePrice(newSalePrice)
                    .previousMarginPercent(prevMargin)
                    .marginPercent(targetMargin)
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

    private BigDecimal computeSalePrice(BigDecimal cost, BigDecimal targetMarginPercent) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0 ||
            targetMarginPercent == null || targetMarginPercent.compareTo(BigDecimal.ZERO) < 0 ||
            targetMarginPercent.compareTo(new BigDecimal("100")) >= 0) {
            return cost != null ? cost : BigDecimal.ZERO;
        }
        BigDecimal factor = BigDecimal.ONE.subtract(targetMarginPercent.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP));
        return cost.divide(factor, 2, java.math.RoundingMode.HALF_UP);
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
                        .marginPercent(l.getMarginPercent())
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
                .marginPercent(pa.getMarginPercent())
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
                                                    Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();

        List<SalesDocumentDto> docs = new ArrayList<>();

        boolean includeSales = kind == null || kind.isBlank() || "sale".equalsIgnoreCase(kind);
        boolean includeQuotations = kind == null || kind.isBlank() || "quotation".equalsIgnoreCase(kind);

        if (includeSales) {
            List<Invoice> invoices = invoiceRepository.findByCompanyId(companyId);
            for (Invoice inv : invoices) {
                if (branchId != null && inv.getBranch() != null && !branchId.equals(inv.getBranch().getId())) {
                    continue;
                }
                if (customerId != null && inv.getCustomer() != null && !customerId.equals(inv.getCustomer().getId())) {
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
                docs.add(toQuotationDocumentDto(so));
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
        BigDecimal receivable = BigDecimal.ZERO;

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

            BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal paid = inv.getPayments().stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal balance = total.subtract(paid);
            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                balance = BigDecimal.ZERO;
            }

            collected = collected.add(paid);
            receivable = receivable.add(balance);
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
                .receivable(receivable)
                .activeQuotations(activeQuotations)
                .build();
    }

    @Transactional(readOnly = true)
    public NextFolioPreviewDto getNextFolioPreview() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        long salesCount = invoiceRepository.countByCompanyId(companyId) + 1;
        long quotationCount = salesOrderRepository.countByCompanyId(companyId) + 1;

        return NextFolioPreviewDto.builder()
                .sale(String.format("V-%05d", salesCount))
                .quotation(String.format("COT-%05d", quotationCount))
                .build();
    }

    private SalesDocumentDto toSaleDocumentDto(Invoice inv) {
        String folio = inv.getSeries() != null && inv.getNumber() != null
                ? inv.getSeries() + "-" + inv.getNumber()
                : "V-" + String.format("%05d", inv.getId());

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

        String paymentMethod = !inv.getPayments().isEmpty()
                ? inv.getPayments().get(0).getPaymentMethod().toLowerCase()
                : "cash";

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

    private SalesDocumentDto toQuotationDocumentDto(SalesOrder so) {
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
                .build();
    }
}
