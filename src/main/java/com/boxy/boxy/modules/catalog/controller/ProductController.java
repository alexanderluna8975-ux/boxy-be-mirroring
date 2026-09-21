package com.boxy.boxy.modules.catalog.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.modules.catalog.dto.*;
import com.boxy.boxy.modules.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Catalog & Products", description = "Endpoints for managing products, categories, brands, and units")
public class ProductController {

    private final ProductService productService;
    private final com.boxy.boxy.modules.catalog.service.InventoryImportService inventoryImportService;
    private final com.boxy.boxy.modules.inventory.service.InventoryService inventoryService;

    @GetMapping("/products")
    @Operation(summary = "Get paginated list of products with filters")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, name = "filter.categoryId") Long filterCategoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false, name = "filter.brandId") Long filterBrandId,
            @RequestParam(required = false, name = "filter.status") String filterStatus,
            @RequestParam(required = false) Boolean isActive) {
        int effectiveLimit = pageSize != null ? pageSize : (limit != null ? limit : 20);
        Long effectiveCategoryId = categoryId != null ? categoryId : filterCategoryId;
        Long effectiveBrandId = brandId != null ? brandId : filterBrandId;
        Boolean effectiveIsActive = isActive;
        if (effectiveIsActive == null && filterStatus != null && !filterStatus.isBlank()) {
            if ("active".equalsIgnoreCase(filterStatus)) {
                effectiveIsActive = true;
            } else if ("archived".equalsIgnoreCase(filterStatus)) {
                effectiveIsActive = false;
            }
        }

        Page<ProductDto> paged = productService.getProducts(
                search, effectiveCategoryId, effectiveBrandId, effectiveIsActive,
                PageRequest.of(page - 1, effectiveLimit, Sort.by("id").ascending()));
        PageMeta meta = PageMeta.of(page, effectiveLimit, paged.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ApiResponse<ProductDto>> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductById(id)));
    }

    @GetMapping("/products/{id}/metrics")
    @Operation(summary = "Get computed stock/value/margin metrics for a product's detail page")
    public ResponseEntity<ApiResponse<ProductMetricsDto>> getProductMetrics(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getMetrics(id)));
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Update an existing product")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody CreateProductRequest request) {
        ProductDto updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Product updated successfully"));
    }

    @PatchMapping("/products/{id}/archive")
    @Operation(summary = "Archive or unarchive a product")
    public ResponseEntity<ApiResponse<ProductDto>> archiveProduct(@PathVariable Long id) {
        ProductDto updated = productService.archiveProduct(id);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Product status updated"));
    }

    @GetMapping("/products/check-unique")
    @Operation(summary = "Check uniqueness of SKU or barcode")
    public ResponseEntity<java.util.Map<String, Boolean>> checkUnique(
            @RequestParam String field,
            @RequestParam String value,
            @RequestParam(required = false) Long excludeId) {
        boolean available = productService.isUnique(field, value, excludeId);
        return ResponseEntity.ok(java.util.Map.of("available", available));
    }

    @GetMapping("/products/{id}/stock")
    @Operation(summary = "Get stock levels for a product across warehouses")
    public ResponseEntity<ApiResponse<List<com.boxy.boxy.modules.inventory.dto.StockLevelDto>>> getProductStock(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getStockLevelsByProduct(id)));
    }

    @GetMapping("/products/{id}/cost-history")
    @Operation(summary = "Get the cost history for a product — one entry per goods receipt that recalculated its cost")
    public ResponseEntity<ApiResponse<List<ProductCostHistoryDto>>> getProductCostHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer pageSize) {
        Page<ProductCostHistoryDto> paged = productService.getCostHistory(
                id, PageRequest.of(page - 1, pageSize != null ? pageSize : 20));
        PageMeta meta = PageMeta.of(page, pageSize != null ? pageSize : 20, paged.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @PostMapping("/products")
    @Operation(summary = "Create a new product")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductDto created = productService.createProduct(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Product created successfully"), HttpStatus.CREATED);
    }

    @PostMapping(value = "/products/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('products:create') or hasAuthority('inventory:write') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Import products, catalog or inventory from Excel or CSV file")
    public ResponseEntity<ApiResponse<ImportResultDto>> importProducts(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId) {
        ImportResultDto result = inventoryImportService.importFile(file, warehouseId);
        return ResponseEntity.ok(ApiResponse.ok(result, result.getMessage()));
    }

    @GetMapping("/categories")
    @Operation(summary = "List all product categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getCategories()));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('products:create') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Create a product category")
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(@Valid @RequestBody CategoryDto request) {
        CategoryDto created = productService.createCategory(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Category created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('products:update') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update a product category")
    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryDto request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateCategory(id, request), "Category updated successfully"));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('products:delete') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Delete a product category (rejected if any product still uses it)")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        productService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Category deleted successfully"));
    }

    @GetMapping("/brands")
    @Operation(summary = "List all product brands")
    public ResponseEntity<ApiResponse<List<BrandDto>>> getBrands() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getBrands()));
    }

    @PostMapping("/brands")
    @PreAuthorize("hasAuthority('products:create') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Create a product brand")
    public ResponseEntity<ApiResponse<BrandDto>> createBrand(@Valid @RequestBody BrandDto request) {
        BrandDto created = productService.createBrand(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Brand created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('products:update') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update a product brand")
    public ResponseEntity<ApiResponse<BrandDto>> updateBrand(@PathVariable Long id, @Valid @RequestBody BrandDto request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateBrand(id, request), "Brand updated successfully"));
    }

    @DeleteMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('products:delete') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Delete a product brand (rejected if any product still uses it)")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable Long id) {
        productService.deleteBrand(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Brand deleted successfully"));
    }

    @GetMapping("/units")
    @Operation(summary = "List all units of measure")
    public ResponseEntity<ApiResponse<List<UnitDto>>> getUnits() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getUnits()));
    }

    @PostMapping("/units")
    @PreAuthorize("hasAuthority('products:create') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Create a unit of measure")
    public ResponseEntity<ApiResponse<UnitDto>> createUnit(@Valid @RequestBody UnitDto request) {
        UnitDto created = productService.createUnit(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Unit created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/units/{id}")
    @PreAuthorize("hasAuthority('products:update') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update a unit of measure")
    public ResponseEntity<ApiResponse<UnitDto>> updateUnit(@PathVariable Long id, @Valid @RequestBody UnitDto request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateUnit(id, request), "Unit updated successfully"));
    }

    @DeleteMapping("/units/{id}")
    @PreAuthorize("hasAuthority('products:delete') or hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Delete a unit of measure (rejected if any product still uses it)")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(@PathVariable Long id) {
        productService.deleteUnit(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Unit deleted successfully"));
    }
}
