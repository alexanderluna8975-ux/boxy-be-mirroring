package com.boxy.boxy.modules.sales.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.sales.dto.*;
import com.boxy.boxy.modules.sales.service.SalesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
@Tag(name = "Sales & POS", description = "Endpoints for point-of-sale checkout, shifts, invoices and customers")
public class SalesController {

    private final SalesService salesService;

    @GetMapping("/customers")
    @Operation(summary = "List all active customers")
    public ResponseEntity<ApiResponse<List<CustomerDto>>> getAllCustomers() {
        return ResponseEntity.ok(ApiResponse.ok(salesService.getAllCustomers()));
    }

    @PostMapping("/customers")
    @Operation(summary = "Create a new customer")
    public ResponseEntity<ApiResponse<CustomerDto>> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerDto created = salesService.createCustomer(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Customer created successfully"), HttpStatus.CREATED);
    }

    @PostMapping("/sessions/open")
    @Operation(summary = "Open a new cashier shift session")
    public ResponseEntity<ApiResponse<CashierSessionDto>> openSession(@Valid @RequestBody OpenSessionRequest request) {
        CashierSessionDto session = salesService.openSession(request);
        return new ResponseEntity<>(ApiResponse.ok(session, "Cashier session opened successfully"), HttpStatus.CREATED);
    }

    @PostMapping("/sessions/{id}/close")
    @Operation(summary = "Close an active cashier shift session")
    public ResponseEntity<ApiResponse<CashierSessionDto>> closeSession(
            @PathVariable Long id,
            @Valid @RequestBody CloseSessionRequest request) {
        CashierSessionDto session = salesService.closeSession(id, request);
        return ResponseEntity.ok(ApiResponse.ok(session, "Cashier session closed successfully"));
    }

    @GetMapping("/sessions/active")
    @Operation(summary = "Get the active cashier shift session for the current user and branch")
    public ResponseEntity<ApiResponse<CashierSessionDto>> getActiveSession(@RequestParam Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.getActiveSession(branchId).orElse(null)));
    }

    @PostMapping({"/checkout", "/sales"})
    @Operation(summary = "Execute atomic POS checkout (stock deduction, invoice and payment)")
    public ResponseEntity<ApiResponse<InvoiceDto>> checkout(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        if (idempotencyKey != null && request.getIdempotencyKey() == null) {
            request.setIdempotencyKey(idempotencyKey);
        }
        InvoiceDto invoice = salesService.processCheckout(request);
        return new ResponseEntity<>(ApiResponse.ok(invoice, "Sale completed successfully"), HttpStatus.CREATED);
    }

    @GetMapping("/invoices")
    @Operation(summary = "List sales invoices with pagination")
    public ResponseEntity<ApiResponse<List<InvoiceDto>>> getInvoices(
            @RequestParam(required = false) Long branchId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<InvoiceDto> page = salesService.getInvoices(branchId, pageable);
        return ResponseEntity.ok(ApiResponse.paged(page.getContent(), com.boxy.boxy.core.response.PageMeta.from(page)));
    }
}