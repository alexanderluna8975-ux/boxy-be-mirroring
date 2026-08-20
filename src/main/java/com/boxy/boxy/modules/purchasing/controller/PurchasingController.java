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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/purchasing")
@RequiredArgsConstructor
@Tag(name = "Purchasing & Procurement", description = "Endpoints for managing suppliers, purchase orders, and goods receipts")
public class PurchasingController {

    private final PurchasingService purchasingService;

    @GetMapping("/suppliers")
    @Operation(summary = "List all suppliers")
    public ResponseEntity<ApiResponse<List<SupplierDto>>> getSuppliers() {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.getAllSuppliers()));
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('purchasing:order') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Create a new supplier")
    public ResponseEntity<ApiResponse<SupplierDto>> createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        SupplierDto created = purchasingService.createSupplier(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Supplier created successfully"), HttpStatus.CREATED);
    }

    @GetMapping("/orders")
    @Operation(summary = "Get paginated list of purchase orders")
    public ResponseEntity<ApiResponse<List<PurchaseOrderDto>>> getPurchaseOrders(
            @RequestParam(required = false) String branchId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Page<PurchaseOrderDto> paged = purchasingService.getPurchaseOrders(branchId, PageRequest.of(page - 1, limit));
        PageMeta meta = PageMeta.of(page, limit, paged.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('purchasing:order') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Create a new purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> createPurchaseOrder(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        PurchaseOrderDto created = purchasingService.createPurchaseOrder(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Purchase order created successfully"), HttpStatus.CREATED);
    }

    @PostMapping("/goods-receipts")
    @PreAuthorize("hasAuthority('purchasing:receipt') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Receive goods into warehouse inventory from a purchase order")
    public ResponseEntity<ApiResponse<Void>> receiveGoods(@Valid @RequestBody CreateGoodsReceiptRequest request) {
        purchasingService.receiveGoods(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Goods received and inventory updated successfully"));
    }
}
