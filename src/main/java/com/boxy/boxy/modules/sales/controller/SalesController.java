package com.boxy.boxy.modules.sales.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.modules.sales.dto.*;
import com.boxy.boxy.modules.sales.service.SalesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
@Tag(name = "Sales & POS", description = "Endpoints for cashier sessions, customers, POS checkout, and invoicing")
public class SalesController {

    private final SalesService salesService;

    @GetMapping("/customers")
    @Operation(summary = "List all customers")
    public ResponseEntity<ApiResponse<List<CustomerDto>>> getCustomers() {
        return ResponseEntity.ok(ApiResponse.ok(salesService.getAllCustomers()));
    }

    @PostMapping("/customers")
    @Operation(summary = "Create a new customer")
    public ResponseEntity<ApiResponse<CustomerDto>> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerDto created = salesService.createCustomer(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Customer created successfully"), HttpStatus.CREATED);
    }

    @GetMapping("/sessions/active")
    @Operation(summary = "Get current user's active cashier session in branch")
    public ResponseEntity<ApiResponse<CashierSessionDto>> getActiveSession(@RequestParam String branchId) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.getActiveSession(branchId).orElse(null)));
    }

    @PostMapping("/sessions/open")
    @PreAuthorize("hasAuthority('sales:session') or hasAuthority('ROLE_CASHIER') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Open a new cashier shift session")
    public ResponseEntity<ApiResponse<CashierSessionDto>> openSession(@Valid @RequestBody OpenSessionRequest request) {
        CashierSessionDto session = salesService.openSession(request);
        return new ResponseEntity<>(ApiResponse.ok(session, "Cashier session opened successfully"), HttpStatus.CREATED);
    }

    @PostMapping("/sessions/{id}/close")
    @PreAuthorize("hasAuthority('sales:session') or hasAuthority('ROLE_CASHIER') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Close an active cashier shift session")
    public ResponseEntity<ApiResponse<CashierSessionDto>> closeSession(@PathVariable String id, @Valid @RequestBody CloseSessionRequest request) {
        CashierSessionDto session = salesService.closeSession(id, request);
        return ResponseEntity.ok(ApiResponse.ok(session, "Cashier session closed successfully"));
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAuthority('sales:checkout') or hasAuthority('ROLE_CASHIER') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Execute atomic POS checkout with pessimistic stock lock and invoice generation")
    public ResponseEntity<ApiResponse<InvoiceDto>> checkout(@Valid @RequestBody CheckoutRequest request) {
        InvoiceDto invoice = salesService.processCheckout(request);
        return new ResponseEntity<>(ApiResponse.ok(invoice, "Sale processed and invoice issued successfully"), HttpStatus.CREATED);
    }

    @GetMapping("/invoices")
    @Operation(summary = "Get paginated invoices")
    public ResponseEntity<ApiResponse<List<InvoiceDto>>> getInvoices(
            @RequestParam(required = false) String branchId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Page<InvoiceDto> paged = salesService.getInvoices(branchId, PageRequest.of(page - 1, limit));
        PageMeta meta = PageMeta.of(page, limit, paged.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }
}
