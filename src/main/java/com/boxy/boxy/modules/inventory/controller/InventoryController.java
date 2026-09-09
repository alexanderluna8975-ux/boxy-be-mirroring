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
    private final com.boxy.boxy.modules.catalog.service.InventoryImportService inventoryImportService;

    @PostMapping(value = "/stock/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('inventory:write') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Import warehouse inventory stock levels from Excel or CSV file")
    public ResponseEntity<ApiResponse<com.boxy.boxy.modules.catalog.dto.ImportResultDto>> importStock(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId) {
        com.boxy.boxy.modules.catalog.dto.ImportResultDto result = inventoryImportService.importFile(file, warehouseId);
        return ResponseEntity.ok(ApiResponse.ok(result, result.getMessage()));
    }

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

    @GetMapping("/movements")
    @Operation(summary = "Get all inventory Kardex movements with optional warehouse and product filters")
    public ResponseEntity<ApiResponse<List<StockMovementDto>>> getAllMovements(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "20") int pageSize) {
        int finalLimit = Math.max(limit, pageSize);
        Page<StockMovementDto> paged = inventoryService.getAllMovements(warehouseId, productId, PageRequest.of(page - 1, finalLimit));
        PageMeta meta = PageMeta.of(page, finalLimit, paged.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping("/transfers")
    @Operation(summary = "List all inventory transfers paginated")
    public ResponseEntity<ApiResponse<List<StockTransferDto>>> getTransfers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "20") int pageSize) {
        int finalLimit = Math.max(limit, pageSize);
        Page<StockTransferDto> paged = inventoryService.getAllTransfers(PageRequest.of(page - 1, finalLimit));
        PageMeta meta = PageMeta.of(page, finalLimit, paged.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping("/transfers/{id}")
    @Operation(summary = "Get inventory transfer by ID")
    public ResponseEntity<ApiResponse<StockTransferDto>> getTransferById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getTransferById(id)));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Create an inter-warehouse / inter-branch stock transfer request")
    public ResponseEntity<ApiResponse<StockTransferDto>> createTransfer(@Valid @RequestBody CreateStockTransferRequest request) {
        StockTransferDto created = inventoryService.createTransfer(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Transfer created successfully"), HttpStatus.CREATED);
    }

    @PatchMapping({"/transfers/{id}/approve"})
    @Operation(summary = "Approve requested transfer")
    public ResponseEntity<ApiResponse<StockTransferDto>> approveTransfer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.approveTransfer(id)));
    }

    @PatchMapping({"/transfers/{id}/reject"})
    @Operation(summary = "Reject requested transfer")
    public ResponseEntity<ApiResponse<StockTransferDto>> rejectTransfer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.rejectTransfer(id)));
    }

    @PatchMapping({"/transfers/{id}/ship"})
    @PostMapping("/transfers/{id}/dispatch")
    @Operation(summary = "Ship/dispatch requested transfer (locks and deducts stock from origin)")
    public ResponseEntity<ApiResponse<StockTransferDto>> dispatchTransfer(@PathVariable Long id) {
        StockTransferDto dispatched = inventoryService.dispatchTransfer(id);
        return ResponseEntity.ok(ApiResponse.ok(dispatched, "Transfer dispatched and in transit"));
    }

    @PatchMapping({"/transfers/{id}/receive"})
    @PostMapping("/transfers/{id}/receive")
    @Operation(summary = "Receive transferred items into destination warehouse")
    public ResponseEntity<ApiResponse<StockTransferDto>> receiveTransfer(@PathVariable Long id) {
        StockTransferDto received = inventoryService.receiveTransfer(id);
        return ResponseEntity.ok(ApiResponse.ok(received, "Transfer received and stock added"));
    }
}
