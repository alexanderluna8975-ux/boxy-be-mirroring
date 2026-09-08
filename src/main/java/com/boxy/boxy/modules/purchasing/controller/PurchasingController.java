package com.boxy.boxy.modules.purchasing.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.purchasing.dto.*;
import com.boxy.boxy.modules.purchasing.service.PurchasingService;
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
@RequestMapping("/api/v1/purchasing")
@RequiredArgsConstructor
@Tag(name = "Purchasing & Procurement", description = "Endpoints for managing suppliers, purchase orders and goods receipts")
public class PurchasingController {

    private final PurchasingService purchasingService;

    @GetMapping("/suppliers")
    @Operation(summary = "List all active suppliers")
    public ResponseEntity<ApiResponse<List<SupplierDto>>> getAllSuppliers() {
        return ResponseEntity.ok(ApiResponse.ok(purchasingService.getAllSuppliers()));
    }

    @PostMapping("/suppliers")
    @Operation(summary = "Create a new supplier")
    public ResponseEntity<ApiResponse<SupplierDto>> createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        SupplierDto created = purchasingService.createSupplier(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Supplier created successfully"), HttpStatus.CREATED);
    }

    @GetMapping("/orders")
    @Operation(summary = "List purchase orders with pagination")
    public ResponseEntity<ApiResponse<List<PurchaseOrderDto>>> getPurchaseOrders(
            @RequestParam(required = false) Long branchId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PurchaseOrderDto> page = purchasingService.getPurchaseOrders(branchId, pageable);
        return ResponseEntity.ok(ApiResponse.paged(page.getContent(), com.boxy.boxy.core.response.PageMeta.from(page)));
    }

    @PostMapping("/orders")
    @Operation(summary = "Create a new purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderDto>> createPurchaseOrder(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        PurchaseOrderDto created = purchasingService.createPurchaseOrder(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Purchase order created successfully"), HttpStatus.CREATED);
    }

    @PostMapping({"/goods-receipts", "/receiving"})
    @Operation(summary = "Receive goods into warehouse inventory from a purchase order")
    public ResponseEntity<ApiResponse<Void>> receiveGoods(@Valid @RequestBody CreateGoodsReceiptRequest request) {
        purchasingService.receiveGoods(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Goods received successfully"));
    }
}