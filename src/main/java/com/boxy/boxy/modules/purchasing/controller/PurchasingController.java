package com.boxy.boxy.modules.purchasing.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.modules.purchasing.dto.*;
import com.boxy.boxy.modules.purchasing.service.PurchasingService;
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
@RequestMapping("/api/v1/purchasing")
@RequiredArgsConstructor
@Tag(name = "Purchasing & Procurement", description = "Endpoints for managing suppliers, purchase orders and goods receipts")
public class PurchasingController {

    private final PurchasingService purchasingService;

    // --- SUPPLIERS ---

    @GetMapping("/suppliers")
    @Operation(summary = "List all active suppliers")
    public ResponseEntity<ApiResponse<List<SupplierDto>>> getAllSuppliers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer pageSize) {
        if (page != null || limit != null || pageSize != null) {
            int p = page != null ? page : 1;
            int l = limit != null ? limit : (pageSize != null ? pageSize : 20);
            Page<SupplierDto> paged = purchasingService.getSuppliersPaged(PageRequest.of(p - 1, l));
            return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), PageMeta.of(p, l, paged.getTotalElements())));
        }
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.getAllSuppliers()));
    }

    @GetMapping("/suppliers/{id}")
    @Operation(summary = "Get supplier details by ID")
    public ResponseEntity<ApiResponse<SupplierDto>> getSupplierById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.getSupplierById(id)));
    }

    @PostMapping("/suppliers")
    @Operation(summary = "Create a new supplier")
    public ResponseEntity<ApiResponse<SupplierDto>> createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        SupplierDto created = purchasingService.createSupplier(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Supplier created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/suppliers/{id}")
    @Operation(summary = "Update supplier by ID")
    public ResponseEntity<ApiResponse<SupplierDto>> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.updateSupplier(id, request), "Supplier updated successfully"));
    }

    @PatchMapping("/suppliers/{id}/deactivate")
    @Operation(summary = "Toggle supplier active status")
    public ResponseEntity<ApiResponse<SupplierDto>> deactivateSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.deactivateSupplier(id)));
    }

    @GetMapping("/suppliers/check-unique")
    @Operation(summary = "Check whether a supplier taxId is unique")
    public ResponseEntity<Map<String, Boolean>> checkSupplierUnique(
            @RequestParam String field,
            @RequestParam String value,
            @RequestParam(required = false) Long excludeId) {
        boolean available = purchasingService.checkSupplierUnique(field, value, excludeId);
        return ResponseEntity.ok(Collections.singletonMap("available", available));
    }

    // --- PURCHASE ORDERS ---

    @GetMapping("/orders")
    @Operation(summary = "List purchase orders with pagination")
    public ResponseEntity<ApiResponse<List<PurchaseOrderDto>>> getPurchaseOrders(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "filter.status") String status,
            @RequestParam(required = false, name = "filter.supplierId") Long supplierId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "20") int pageSize) {
        int finalLimit = Math.max(limit, pageSize);
        Page<PurchaseOrderDto> p = purchasingService.getPurchaseOrders(
                branchId, status, supplierId, search, PageRequest.of(page - 1, finalLimit));
        return ResponseEntity.ok(ApiResponse.paged(p.getContent(), PageMeta.of(page, finalLimit, p.getTotalElements())));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get purchase order by ID")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> getPurchaseOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.getPurchaseOrderById(id)));
    }

    @GetMapping("/orders/{id}/pdf")
    @Operation(summary = "Download the purchase order as a printable PDF")
    public ResponseEntity<byte[]> getPurchaseOrderPdf(@PathVariable Long id) {
        byte[] pdf = purchasingService.generatePurchaseOrderPdf(id);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"orden-compra-" + id + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/orders")
    @Operation(summary = "Create a new purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> createPurchaseOrder(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        PurchaseOrderDto created = purchasingService.createPurchaseOrder(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Purchase order created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/orders/{id}")
    @Operation(summary = "Update purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> updatePurchaseOrder(
            @PathVariable Long id,
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.updatePurchaseOrder(id, request)));
    }

    @PatchMapping("/orders/{id}/submit")
    @Operation(summary = "Submit purchase order for approval")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> submitPurchaseOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.submitPurchaseOrder(id)));
    }

    @PatchMapping("/orders/{id}/approve")
    @Operation(summary = "Approve purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> approvePurchaseOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.approvePurchaseOrder(id)));
    }

    @PatchMapping("/orders/{id}/reject")
    @Operation(summary = "Reject purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> rejectPurchaseOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : "Rechazado por administración";
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.rejectPurchaseOrder(id, reason)));
    }

    @PatchMapping("/orders/{id}/mark-ordered")
    @Operation(summary = "Mark purchase order as ordered to supplier")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> markPurchaseOrderOrdered(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.markPurchaseOrderOrdered(id)));
    }

    @PatchMapping("/orders/{id}/cancel")
    @Operation(summary = "Cancel purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> cancelPurchaseOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.cancelPurchaseOrder(id)));
    }

    // --- RECEIVING / GOODS RECEIPTS ---

    @GetMapping({"/goods-receipts", "/receiving"})
    @Operation(summary = "List goods receipts paginated")
    public ResponseEntity<ApiResponse<List<GoodsReceiptDto>>> getAllGoodsReceipts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "20") int pageSize) {
        int finalLimit = Math.max(limit, pageSize);
        Page<GoodsReceiptDto> p = purchasingService.getGoodsReceiptsPaged(PageRequest.of(page - 1, finalLimit));
        return ResponseEntity.ok(ApiResponse.paged(p.getContent(), PageMeta.of(page, finalLimit, p.getTotalElements())));
    }

    @GetMapping({"/goods-receipts/{id}", "/receiving/{id}"})
    @Operation(summary = "Get goods receipt by ID")
    public ResponseEntity<ApiResponse<GoodsReceiptDto>> getGoodsReceiptById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.getGoodsReceiptById(id)));
    }

    @GetMapping({"/goods-receipts/{id}/pdf", "/receiving/{id}/pdf"})
    @Operation(summary = "Download the goods receipt as a printable PDF")
    public ResponseEntity<byte[]> getGoodsReceiptPdf(@PathVariable Long id) {
        byte[] pdf = purchasingService.generateGoodsReceiptPdf(id);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"recepcion-" + id + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/receiving/pending")
    @Operation(summary = "List purchase orders pending goods receipt")
    public ResponseEntity<ApiResponse<List<PurchaseOrderDto>>> getPendingOrders() {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.getPendingPurchaseOrders()));
    }

    @PostMapping({"/goods-receipts", "/receiving"})
    @Operation(summary = "Receive goods into warehouse inventory from a purchase order")
    public ResponseEntity<ApiResponse<GoodsReceiptDto>> receiveGoods(@Valid @RequestBody CreateGoodsReceiptRequest request) {
        GoodsReceiptDto receipt = purchasingService.receiveGoods(request);
        return ResponseEntity.ok(ApiResponse.ok(receipt, "Goods received successfully"));
    }
}
