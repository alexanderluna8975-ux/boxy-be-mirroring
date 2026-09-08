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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SalesService {

    private final CustomerRepository customerRepository;
    private final CashierSessionRepository cashierSessionRepository;
    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public List<CustomerDto> getAllCustomers() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return customerRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toCustomerDto)
                .toList();
    }

    @Transactional
    public CustomerDto createCustomer(CreateCustomerRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        if (customerRepository.findByCompanyIdAndDocumentNumberAndDeletedAtIsNull(companyId, request.getDocumentNumber()).isPresent()) {
            throw new BusinessException("CUSTOMER_EXISTS", "A customer with document number '" + request.getDocumentNumber() + "' already exists.");
        }

        Customer customer = Customer.builder()
                .company(company)
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber().trim())
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

        Long userId = SecurityUtils.getCurrentUserId();
        CashierSession session = cashierSessionRepository.findByUserIdAndBranchIdAndStatus(userId, request.getBranchId(), "OPEN")
                .orElseThrow(() -> new BusinessException("NO_ACTIVE_SESSION", "No active cashier session found for this user in this branch. Please open a shift first."));

        Branch branch = branchRepository.findByIdAndDeletedAtIsNull(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", request.getBranchId()));

        Warehouse warehouse = warehouseRepository.findByIdAndDeletedAtIsNull(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getWarehouseId()));

        Customer customer = customerRepository.findByIdAndDeletedAtIsNull(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.getCustomerId()));

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User not found"));

        Company company = branch.getCompany();

        String series = "B001";
        String number = String.valueOf(System.currentTimeMillis()).substring(3);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        Invoice invoice = Invoice.builder()
                .company(company)
                .branch(branch)
                .warehouse(warehouse)
                .cashierSession(session)
                .customer(customer)
                .documentType(request.getDocumentType().toUpperCase())
                .series(series)
                .number(number)
                .idempotencyKey(request.getIdempotencyKey())
                .status("ISSUED")
                .createdBy(user)
                .build();

        for (var itemReq : request.getItems()) {
            Product product = productRepository.findByIdAndDeletedAtIsNull(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

            // Concurrency safe row-locking: SELECT ... FOR UPDATE
            StockLevel stock = stockLevelRepository.findForUpdate(warehouse.getId(), product.getId())
                    .orElseThrow(() -> new InsufficientStockException(product.getSku(), product.getName(), 0, itemReq.getQuantity().doubleValue()));

            if (stock.getQuantityAvailable().compareTo(itemReq.getQuantity()) < 0 && !Boolean.TRUE.equals(company.getAllowNegativeStock())) {
                throw new InsufficientStockException(product.getSku(), product.getName(), stock.getQuantityAvailable().doubleValue(), itemReq.getQuantity().doubleValue());
            }

            // Deduct stock
            stock.setQuantityAvailable(stock.getQuantityAvailable().subtract(itemReq.getQuantity()));
            stockLevelRepository.save(stock);

            BigDecimal lineGross = itemReq.getQuantity().multiply(itemReq.getUnitPrice());
            BigDecimal lineDiscount = itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal lineNet = lineGross.subtract(lineDiscount);
            BigDecimal lineTax = lineNet.multiply(itemReq.getTaxRate());
            BigDecimal lineTotal = lineNet.add(lineTax);

            subtotal = subtotal.add(lineNet);
            discountTotal = discountTotal.add(lineDiscount);
            taxTotal = taxTotal.add(lineTax);

            InvoiceItem item = InvoiceItem.builder()
                    .invoice(invoice)
                    .product(product)
                    .productName(product.getName())
                    .sku(product.getSku())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .unitCost(product.getCostPrice())
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
                    .quantity(itemReq.getQuantity().negate())
                    .unitCost(product.getCostPrice())
                    .balanceAfter(stock.getQuantityAvailable())
                    .referenceType("INVOICE")
                    .referenceId(series + "-" + number)
                    .notes("POS sale to " + customer.getName())
                    .createdBy(user)
                    .build();
            stockMovementRepository.save(movement);
        }

        invoice.setSubtotal(subtotal);
        invoice.setDiscountAmount(discountTotal);
        invoice.setTaxAmount(taxTotal);
        invoice.setTotalAmount(subtotal.add(taxTotal));

        // Add Payments
        BigDecimal cashPaid = BigDecimal.ZERO;
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

        // Update Session expected cash
        session.setExpectedCash(session.getExpectedCash().add(cashPaid));
        cashierSessionRepository.save(session);

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
        return CustomerDto.builder()
                .id(c.getId())
                .documentType(c.getDocumentType())
                .documentNumber(c.getDocumentNumber())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .address(c.getAddress())
                .creditLimit(c.getCreditLimit())
                .currentCredit(c.getCurrentCredit())
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
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .unitCost(i.getUnitCost())
                        .discountAmount(i.getDiscountAmount())
                        .taxAmount(i.getTaxAmount())
                        .totalAmount(i.getTotalAmount())
                        .build())
                .toList();

        List<PaymentDto> payDtos = inv.getPayments().stream()
                .map(p -> PaymentDto.builder()
                        .id(p.getId())
                        .paymentMethod(p.getPaymentMethod())
                        .amount(p.getAmount())
                        .referenceCode(p.getReferenceCode())
                        .status(p.getStatus())
                        .createdAt(p.getCreatedAt())
                        .build())
                .toList();

        return InvoiceDto.builder()
                .id(inv.getId())
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
                .totalAmount(inv.getTotalAmount())
                .status(inv.getStatus())
                .createdByName(inv.getCreatedBy().getFullName())
                .items(itemDtos)
                .payments(payDtos)
                .createdAt(inv.getCreatedAt())
                .build();
    }
}
