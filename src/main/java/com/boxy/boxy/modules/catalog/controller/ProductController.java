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

    @GetMapping("/products")
    @Operation(summary = "Get paginated list of products with filters")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) Boolean isActive) {
        Page<ProductDto> paged = productService.getProducts(
                search, categoryId, brandId, isActive,
                PageRequest.of(page - 1, limit, Sort.by("createdAt").descending()));
        PageMeta meta = PageMeta.of(page, limit, paged.getTotalElements());
        return ResponseEntity.ok(ApiResponse.paged(paged.getContent(), meta));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ApiResponse<ProductDto>> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductById(id)));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('inventory:write') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Create a new product")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductDto created = productService.createProduct(request);
        return new ResponseEntity<>(ApiResponse.ok(created, "Product created successfully"), HttpStatus.CREATED);
    }

    @GetMapping("/categories")
    @Operation(summary = "List all product categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getCategories()));
    }

    @GetMapping("/brands")
    @Operation(summary = "List all product brands")
    public ResponseEntity<ApiResponse<List<BrandDto>>> getBrands() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getBrands()));
    }

    @GetMapping("/units")
    @Operation(summary = "List all units of measure")
    public ResponseEntity<ApiResponse<List<UnitDto>>> getUnits() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getUnits()));
    }
}
