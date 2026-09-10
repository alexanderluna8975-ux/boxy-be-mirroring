package com.boxy.boxy.modules.inventory.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.core.web.PageableFactory;
import com.boxy.boxy.modules.inventory.dto.AdjustmentReasonDto;
import com.boxy.boxy.modules.inventory.dto.CreateStockAdjustmentRequest;
import com.boxy.boxy.modules.inventory.dto.StockAdjustmentDto;
import com.boxy.boxy.modules.inventory.service.StockAdjustmentService;
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
@Tag(name = "Stock Adjustments", description = "Endpoints for inventory adjustments, physical counts and shrinkage")
public class StockAdjustmentController {

    private final StockAdjustmentService stockAdjustmentService;

    @GetMapping("/adjustments")
    @PreAuthorize("hasAuthority('inventory:view') or hasAuthority('inventory:read') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "List all inventory adjustments paginated, with search, status, warehouse, reason and date filters")
    public ResponseEntity<ApiResponse<List<StockAdjustmentDto>>> getAdjustments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "filter.status") String status,
            @RequestParam(required = false, name = "filter.warehouseId") Long warehouseId,
            @RequestParam(required = false, name = "filter.reasonId") String reasonId,
            @RequestParam(required = false, name = "filter.dateFrom") String dateFrom,
            @RequestParam(required = false, name = "filter.dateTo") String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer pageSize) {
        Pageable pageable = PageableFactory.of(page, limit, pageSize);
        Page<StockAdjustmentDto> paged = stockAdjustmentService.getAdjustments(
                search, status, warehouseId, reasonId, dateFrom, dateTo, pageable);
        PageMeta meta = PageMeta.from(paged);
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping({"/adjustment-reasons", "/adjustments/reasons"})
    @PreAuthorize("hasAuthority('inventory:view') or hasAuthority('inventory:read') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "List available stock adjustment reasons")
    public ResponseEntity<ApiResponse<List<AdjustmentReasonDto>>> getAdjustmentReasons() {
        return ResponseEntity.ok(ApiResponse.ok(stockAdjustmentService.getReasons()));
    }

    @GetMapping("/adjustments/{id:\\d+}")
    @PreAuthorize("hasAuthority('inventory:view') or hasAuthority('inventory:read') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get adjustment by ID")
    public ResponseEntity<ApiResponse<StockAdjustmentDto>> getAdjustmentById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(stockAdjustmentService.getAdjustmentById(id)));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('inventory:create') or hasAuthority('inventory:adjust') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Create a new stock adjustment request")
    public ResponseEntity<ApiResponse<StockAdjustmentDto>> createAdjustment(@Valid @RequestBody CreateStockAdjustmentRequest request) {
        StockAdjustmentDto created = stockAdjustmentService.createAdjustment(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Adjustment created successfully"), HttpStatus.CREATED);
    }

    @PatchMapping("/adjustments/{id:\\d+}/approve")
    @PreAuthorize("hasAuthority('inventory:approve') or hasAuthority('inventory:adjust') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Approve stock adjustment and update inventory levels")
    public ResponseEntity<ApiResponse<StockAdjustmentDto>> approveAdjustment(@PathVariable Long id) {
        StockAdjustmentDto approved = stockAdjustmentService.approveAdjustment(id);
        return ResponseEntity.ok(ApiResponse.ok(approved, "Adjustment approved and inventory updated"));
    }

    @PatchMapping("/adjustments/{id:\\d+}/reject")
    @PreAuthorize("hasAuthority('inventory:approve') or hasAuthority('inventory:adjust') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Reject stock adjustment")
    public ResponseEntity<ApiResponse<StockAdjustmentDto>> rejectAdjustment(@PathVariable Long id) {
        StockAdjustmentDto rejected = stockAdjustmentService.rejectAdjustment(id);
        return ResponseEntity.ok(ApiResponse.ok(rejected, "Adjustment rejected"));
    }
}
