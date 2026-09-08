package com.boxy.boxy.modules.inventory.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.modules.inventory.dto.*;
import com.boxy.boxy.modules.inventory.service.InventoryService;
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
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory & Stock Management", description = "Endpoints for warehouse stock levels, movements (Kardex) and transfers")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/warehouses/{warehouseId}/stock")
    @Operation(summary = "Get current stock levels for a specific warehouse")
    public ResponseEntity<ApiResponse<List<StockLevelDto>>> getStockLevels(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getStockLevelsByWarehouse(warehouseId)));
    }

    @GetMapping("/warehouses/{warehouseId}/movements")
    @Operation(summary = "Get paginated Kardex movements by warehouse")
    public ResponseEntity<ApiResponse<List<StockMovementDto>>> getMovementsByWarehouse(
            @PathVariable Long warehouseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Page<StockMovementDto> paged = inventoryService.getMovementsByWarehouse(warehouseId, PageRequest.of(page - 1, limit));
        PageMeta meta = PageMeta.of(page, limit, paged.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping("/products/{productId}/movements")
    @Operation(summary = "Get paginated Kardex movements for a specific product across all warehouses")
    public ResponseEntity<ApiResponse<List<StockMovementDto>>> getMovementsByProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Page<StockMovementDto> paged = inventoryService.getMovementsByProduct(productId, PageRequest.of(page - 1, limit));
        PageMeta meta = PageMeta.of(page, limit, paged.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('inventory:transfer') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Create an inter-warehouse / inter-branch stock transfer request")
    public ResponseEntity<ApiResponse<StockTransferDto>> createTransfer(@Valid @RequestBody CreateStockTransferRequest request) {
        StockTransferDto created = inventoryService.createTransfer(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Transfer created successfully"), HttpStatus.CREATED);
    }

    @PostMapping("/transfers/{id}/dispatch")
    @PreAuthorize("hasAuthority('inventory:transfer') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Dispatch requested transfer (locks and deducts stock from origin)")
    public ResponseEntity<ApiResponse<StockTransferDto>> dispatchTransfer(@PathVariable Long id) {
        StockTransferDto dispatched = inventoryService.dispatchTransfer(id);
        return ResponseEntity.ok(ApiResponse.ok(dispatched, "Transfer dispatched and in transit"));
    }

    @PostMapping("/transfers/{id}/receive")
    @PreAuthorize("hasAuthority('inventory:transfer') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Receive transferred items into destination warehouse")
    public ResponseEntity<ApiResponse<StockTransferDto>> receiveTransfer(@PathVariable Long id) {
        StockTransferDto received = inventoryService.receiveTransfer(id);
        return ResponseEntity.ok(ApiResponse.ok(received, "Transfer received and stock added"));
    }
}
