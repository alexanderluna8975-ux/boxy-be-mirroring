package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.administration.dto.CreateWarehouseRequest;
import com.boxy.boxy.modules.administration.dto.WarehouseDto;
import com.boxy.boxy.modules.administration.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/inventory/warehouses", "/api/v1/administration/warehouses"})
@RequiredArgsConstructor
@Tag(name = "Warehouses", description = "Endpoints for managing storage facilities and warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @Operation(summary = "List all company warehouses")
    public ResponseEntity<ApiResponse<List<WarehouseDto>>> getAllWarehouses() {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.getAllWarehouses()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get warehouse details by ID")
    public ResponseEntity<ApiResponse<WarehouseDto>> getWarehouseById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.getWarehouseById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new warehouse")
    public ResponseEntity<ApiResponse<WarehouseDto>> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        WarehouseDto created = warehouseService.createWarehouse(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Warehouse created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing warehouse")
    public ResponseEntity<ApiResponse<WarehouseDto>> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody CreateWarehouseRequest request) {
        WarehouseDto updated = warehouseService.updateWarehouse(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Warehouse updated successfully"));
    }
}
