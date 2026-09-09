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
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
@Tag(name = "Sales & POS", description = "Endpoints for point-of-sale checkout, shifts, invoices and customers")
public class SalesController {

    private final SalesService salesService;

    // --- CUSTOMERS ---

    @GetMapping("/customers")
    @Operation(summary = "List all active customers")
    public ResponseEntity<ApiResponse<List<CustomerDto>>> getAllCustomers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer pageSize) {
        if (page != null || limit != null || pageSize != null) {
            int p = page != null ? page : 1;
            int l = limit != null ? limit : (pageSize != null ? pageSize : 20);
            Page<CustomerDto> paged = salesService.getCustomersPaged(PageRequest.of(p - 1, l));
            return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), PageMeta.of(p, l, paged.getTotalElements())));
        }
        return ResponseEntity.ok(ApiResponse.ok(salesService.getAllCustomers()));
    }

    @GetMapping("/customers/{id}")
    @Operation(summary = "Get customer details by ID")
    public ResponseEntity<ApiResponse<CustomerDto>> getCustomerById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.getCustomerById(id)));
    }

    @PostMapping("/customers")
    @Operation(summary = "Create a new customer")
    public ResponseEntity<ApiResponse<CustomerDto>> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerDto created = salesService.createCustomer(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Customer created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/customers/{id}")
    @Operation(summary = "Update an existing customer")
    public ResponseEntity<ApiResponse<CustomerDto>> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.updateCustomer(id, request), "Customer updated successfully"));
    }

    @PatchMapping("/customers/{id}/archive")
    @Operation(summary = "Archive a customer")
    public ResponseEntity<ApiResponse<CustomerDto>> archiveCustomer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.archiveCustomer(id), "Customer archived successfully"));
    }

    @GetMapping("/customers/check-unique")
    @Operation(summary = "Check whether a customer taxId / document is unique")
    public ResponseEntity<Map<String, Boolean>> checkCustomerUnique(
            @RequestParam String field,
            @RequestParam String value,
            @RequestParam(required = false) Long excludeId) {
        boolean available = salesService.checkCustomerUnique(field, value, excludeId);
        return ResponseEntity.ok(Collections.singletonMap("available", available));
    }

    // --- CATALOG LOOKUP FOR POS ---

    @GetMapping("/catalog/search")
    @Operation(summary = "Search product catalog for POS checkout")
    public ResponseEntity<ApiResponse<List<CatalogItemDto>>> searchCatalog(
            @RequestParam(name = "search", required = false) String search) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.searchCatalog(search)));
    }

    @GetMapping("/catalog/barcode/{barcode}")
    @Operation(summary = "Find product catalog item by barcode")
    public ResponseEntity<ApiResponse<CatalogItemDto>> findCatalogByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.findCatalogByBarcode(barcode).orElse(null)));
    }

    // --- QUOTATIONS ---

    @GetMapping("/quotations")
    @Operation(summary = "List quotations with pagination")
    public ResponseEntity<ApiResponse<List<QuotationDto>>> getQuotations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "20") int pageSize) {
        int finalLimit = Math.max(limit, pageSize);
        Page<QuotationDto> paged = salesService.getQuotations(PageRequest.of(page - 1, finalLimit));
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), PageMeta.of(page, finalLimit, paged.getTotalElements())));
    }

    @GetMapping("/quotations/{id}")
    @Operation(summary = "Get quotation by ID")
    public ResponseEntity<ApiResponse<QuotationDto>> getQuotationById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.getQuotationById(id)));
    }

    @PostMapping("/quotations")
    @Operation(summary = "Create a new quotation")
    public ResponseEntity<ApiResponse<QuotationDto>> createQuotation(@Valid @RequestBody CreateQuotationRequest request) {
        QuotationDto created = salesService.createQuotation(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Quotation created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/quotations/{id}")
    @Operation(summary = "Update a quotation")
    public ResponseEntity<ApiResponse<QuotationDto>> updateQuotation(
            @PathVariable Long id,
            @Valid @RequestBody CreateQuotationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.updateQuotation(id, request), "Quotation updated successfully"));
    }

    @PatchMapping("/quotations/{id}/send")
    @Operation(summary = "Mark quotation as sent")
    public ResponseEntity<ApiResponse<QuotationDto>> sendQuotation(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.sendQuotation(id)));
    }

    @PatchMapping("/quotations/{id}/cancel")
    @Operation(summary = "Cancel a quotation")
    public ResponseEntity<ApiResponse<QuotationDto>> cancelQuotation(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.cancelQuotation(id)));
    }

    @PatchMapping("/quotations/{id}/convert")
    @Operation(summary = "Convert quotation to sale/credit")
    public ResponseEntity<ApiResponse<QuotationDto>> convertQuotation(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String status = body != null ? body.get("status") : "converted";
        return ResponseEntity.ok(ApiResponse.ok(salesService.convertQuotation(id, status)));
    }

    // --- CASHIER SESSIONS ---

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
    public ResponseEntity<ApiResponse<CashierSessionDto>> getActiveSession(@RequestParam(required = false, defaultValue = "1") Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.getActiveSession(branchId).orElse(null)));
    }

    // --- CHECKOUT & SALES ---

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

    @GetMapping("/sales/{id}")
    @Operation(summary = "Get completed sale / invoice by ID")
    public ResponseEntity<ApiResponse<InvoiceDto>> getSaleById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(salesService.getSaleById(id)));
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
