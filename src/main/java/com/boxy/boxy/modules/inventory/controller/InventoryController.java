package com.boxy.boxy.modules.inventory.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.core.web.PageableFactory;
import com.boxy.boxy.modules.inventory.dto.*;
import com.boxy.boxy.modules.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    @PreAuthorize("hasAuthority('inventory:create') or hasAuthority('inventory:write') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Import warehouse inventory stock levels from Excel or CSV file")
    public ResponseEntity<ApiResponse<com.boxy.boxy.modules.catalog.dto.ImportResultDto>> importStock(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId) {
        com.boxy.boxy.modules.catalog.dto.ImportResultDto result = inventoryImportService.importFile(file, warehouseId);
        return ResponseEntity.ok(ApiResponse.ok(result, result.getMessage()));
    }

    @GetMapping("/warehouses/{warehouseId}/stock")
    @PreAuthorize("hasAuthority('inventory:view') or hasAuthority('inventory:read') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get current stock levels for a specific warehouse")
    public ResponseEntity<ApiResponse<List<StockLevelDto>>> getStockLevels(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getStockLevelsByWarehouse(warehouseId)));
    }

    @GetMapping("/warehouses/{warehouseId}/movements")
    @PreAuthorize("hasAuthority('inventory:view') or hasAuthority('inventory:read') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get paginated Kardex movements by warehouse")
    public ResponseEntity<ApiResponse<List<StockMovementDto>>> getMovementsByWarehouse(
            @PathVariable Long warehouseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer pageSize) {
        Pageable pageable = PageableFactory.of(page, limit, pageSize);
        Page<StockMovementDto> paged = inventoryService.getMovementsByWarehouse(warehouseId, pageable);
        PageMeta meta = PageMeta.from(paged);
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping("/products/{productId}/movements")
    @PreAuthorize("hasAuthority('inventory:view') or hasAuthority('inventory:read') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get paginated Kardex movements for a specific product across all warehouses")
    public ResponseEntity<ApiResponse<List<StockMovementDto>>> getMovementsByProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer pageSize) {
        Pageable pageable = PageableFactory.of(page, limit, pageSize);
        Page<StockMovementDto> paged = inventoryService.getMovementsByProduct(productId, pageable);
        PageMeta meta = PageMeta.from(paged);
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('inventory:view') or hasAuthority('inventory:read') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get all inventory Kardex movements with optional warehouse, product, type and date filters")
    public ResponseEntity<ApiResponse<List<StockMovementDto>>> getAllMovements(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "filter.warehouseId") Long filterWarehouseId,
            @RequestParam(required = false, name = "filter.productId") Long filterProductId,
            @RequestParam(required = false, name = "filter.type") String type,
            @RequestParam(required = false, name = "filter.dateFrom") String dateFrom,
            @RequestParam(required = false, name = "filter.dateTo") String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer pageSize) {
        Pageable pageable = PageableFactory.of(page, limit, pageSize);
        Long effectiveWarehouseId = warehouseId != null ? warehouseId : filterWarehouseId;
        Long effectiveProductId = productId != null ? productId : filterProductId;
        Page<StockMovementDto> paged = inventoryService.getAllMovements(
                effectiveWarehouseId, effectiveProductId, type, dateFrom, dateTo, search, pageable);
        PageMeta meta = PageMeta.from(paged);
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasAuthority('transfers:view') or hasAuthority('inventory:read') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "List all inventory transfers paginated, with search, status, warehouse and date filters")
    public ResponseEntity<ApiResponse<List<StockTransferDto>>> getTransfers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "filter.status") String status,
            @RequestParam(required = false, name = "filter.originWarehouseId") Long originWarehouseId,
            @RequestParam(required = false, name = "filter.destinationWarehouseId") Long destinationWarehouseId,
            @RequestParam(required = false, name = "filter.dateFrom") String dateFrom,
            @RequestParam(required = false, name = "filter.dateTo") String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer pageSize) {
        Pageable pageable = PageableFactory.of(page, limit, pageSize);
        Page<StockTransferDto> paged = inventoryService.getAllTransfers(
                search, status, originWarehouseId, destinationWarehouseId, dateFrom, dateTo, pageable);
        PageMeta meta = PageMeta.from(paged);
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping("/transfers/{id}")
    @PreAuthorize("hasAuthority('transfers:view') or hasAuthority('inventory:read') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get inventory transfer by ID")
    public ResponseEntity<ApiResponse<StockTransferDto>> getTransferById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getTransferById(id)));
    }

    @GetMapping("/transfers/{id}/pdf")
    @PreAuthorize("hasAuthority('transfers:view') or hasAuthority('inventory:read') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Download the Nota de Transferencia as a printable PDF")
    public ResponseEntity<byte[]> getTransferPdf(@PathVariable Long id) {
        byte[] pdf = inventoryService.generateTransferPdf(id);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"nota-transferencia-" + id + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('transfers:create') or hasAuthority('inventory:transfer') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Create an inter-warehouse / inter-branch stock transfer request")
    public ResponseEntity<ApiResponse<StockTransferDto>> createTransfer(@Valid @RequestBody CreateStockTransferRequest request) {
        StockTransferDto created = inventoryService.createTransfer(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Transfer created successfully"), HttpStatus.CREATED);
    }

    @PatchMapping("/transfers/{id}/lines/{productId}")
    @PreAuthorize("hasAuthority('transfers:approve') or hasAuthority('inventory:transfer') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Correct a single line's quantity, inline, before approval")
    public ResponseEntity<ApiResponse<StockTransferDto>> updateTransferLine(
            @PathVariable Long id, @PathVariable Long productId, @RequestBody UpdateTransferLineRequest request) {
        StockTransferDto updated = inventoryService.updateTransferLine(id, productId, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Transfer line quantity updated"));
    }

    @DeleteMapping("/transfers/{id}/lines/{productId}")
    @PreAuthorize("hasAuthority('transfers:approve') or hasAuthority('inventory:transfer') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Remove a product line before approval")
    public ResponseEntity<ApiResponse<StockTransferDto>> deleteTransferLine(
            @PathVariable Long id, @PathVariable Long productId) {
        StockTransferDto updated = inventoryService.deleteTransferLine(id, productId);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Transfer line removed"));
    }

    @PatchMapping({"/transfers/{id}/approve"})
    @PreAuthorize("hasAuthority('transfers:approve') or hasAuthority('inventory:transfer') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Approve a requested transfer — immediately dispatches it (locks and deducts stock from origin), no separate ship step")
    public ResponseEntity<ApiResponse<StockTransferDto>> approveTransfer(@PathVariable Long id) {
        StockTransferDto approved = inventoryService.approveTransfer(id);
        return ResponseEntity.ok(ApiResponse.ok(approved, "Transfer approved and in transit"));
    }

    @PatchMapping({"/transfers/{id}/reject"})
    @PreAuthorize("hasAuthority('transfers:approve') or hasAuthority('inventory:transfer') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Reject requested transfer")
    public ResponseEntity<ApiResponse<StockTransferDto>> rejectTransfer(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.rejectTransfer(id)));
    }

    @PatchMapping("/transfers/{id}/receive")
    @PreAuthorize("hasAuthority('transfers:receive') or hasAuthority('inventory:transfer') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Receive transferred items into destination warehouse, recording the actual received quantity per line")
    public ResponseEntity<ApiResponse<StockTransferDto>> receiveTransfer(
            @PathVariable Long id, @RequestBody(required = false) ReceiveTransferRequest request) {
        StockTransferDto received = inventoryService.receiveTransfer(id, request);
        return ResponseEntity.ok(ApiResponse.ok(received, "Transfer received and stock added"));
    }
}
