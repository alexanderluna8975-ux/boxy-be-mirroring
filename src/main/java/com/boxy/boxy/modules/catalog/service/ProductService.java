package com.boxy.boxy.modules.catalog.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.Tax;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.TaxRepository;
import com.boxy.boxy.modules.catalog.dto.*;
import com.boxy.boxy.modules.catalog.entity.Brand;
import com.boxy.boxy.modules.catalog.entity.Category;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.entity.UnitOfMeasure;
import com.boxy.boxy.modules.catalog.repository.BrandRepository;
import com.boxy.boxy.modules.catalog.repository.CategoryRepository;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.catalog.repository.UnitOfMeasureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final TaxRepository taxRepository;
    private final CompanyRepository companyRepository;
    private final com.boxy.boxy.modules.inventory.repository.StockLevelRepository stockLevelRepository;

    @Transactional(readOnly = true)
    public Page<ProductDto> getProducts(String search, Long categoryId, Long brandId, Boolean isActive, Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return productRepository.findAllFiltered(companyId, search, categoryId, brandId, isActive, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return toDto(product);
    }

    @Transactional
    public ProductDto createProduct(CreateProductRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        if (productRepository.findByCompanyIdAndSkuAndDeletedAtIsNull(companyId, request.getSku()).isPresent()) {
            throw new BusinessException("SKU_EXISTS", "A product with SKU '" + request.getSku() + "' already exists.");
        }

        UnitOfMeasure unit = unitOfMeasureRepository.findByIdAndDeletedAtIsNull(request.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", request.getUnitId()));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findByIdAndDeletedAtIsNull(request.getCategoryId()).orElse(null);
        }

        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findByIdAndDeletedAtIsNull(request.getBrandId()).orElse(null);
        }

        Tax tax = null;
        if (request.getTaxId() != null) {
            tax = taxRepository.findById(request.getTaxId()).orElse(null);
        }

        Product product = Product.builder()
                .company(company)
                .category(category)
                .brand(brand)
                .unit(unit)
                .tax(tax)
                .sku(request.getSku().toUpperCase().trim())
                .barcode(request.getBarcode())
                .name(request.getName().trim())
                .description(request.getDescription())
                .costPrice(request.getCostPrice())
                .sellingPrice(request.getSellingPrice())
                .minStockAlert(request.getMinStockAlert() != null ? request.getMinStockAlert() : BigDecimal.ZERO)
                .imageUrl(request.getImageUrl())
                .hasVariants(false)
                .isActive(true)
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();

        Product saved = productRepository.save(product);
        return toDto(saved);
    }

    @Transactional
    public ProductDto updateProduct(Long id, CreateProductRequest request) {
        Product p = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findByIdAndDeletedAtIsNull(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
            p.setCategory(cat);
        }
        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findByIdAndDeletedAtIsNull(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand", request.getBrandId()));
            p.setBrand(brand);
        }
        if (request.getUnitOfMeasureId() != null) {
            UnitOfMeasure uom = unitOfMeasureRepository.findByIdAndDeletedAtIsNull(request.getUnitOfMeasureId())
                    .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", request.getUnitOfMeasureId()));
            p.setUnitOfMeasure(uom);
        }

        if (request.getSku() != null) p.setSku(request.getSku().trim().toUpperCase());
        if (request.getBarcode() != null) p.setBarcode(request.getBarcode().trim());
        if (request.getName() != null) p.setName(request.getName().trim());
        if (request.getDescription() != null) p.setDescription(request.getDescription());
        if (request.getCostPrice() != null) p.setCostPrice(request.getCostPrice());
        if (request.getSellingPrice() != null) p.setSellingPrice(request.getSellingPrice());
        if (request.getMinStockAlert() != null) p.setMinStockAlert(request.getMinStockAlert());
        if (request.getImageUrl() != null) p.setImageUrl(request.getImageUrl());

        return toDto(productRepository.save(p));
    }

    @Transactional
    public ProductDto archiveProduct(Long id) {
        Product p = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        p.setIsActive(!Boolean.TRUE.equals(p.getIsActive()));
        return toDto(productRepository.save(p));
    }

    @Transactional(readOnly = true)
    public boolean isUnique(String field, String value, Long excludeId) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        if ("sku".equalsIgnoreCase(field)) {
            return productRepository.findByCompanyIdAndSkuIgnoreCaseAndDeletedAtIsNull(companyId, value)
                    .map(p -> p.getId().equals(excludeId))
                    .orElse(true);
        } else if ("barcode".equalsIgnoreCase(field)) {
            return productRepository.findByCompanyIdAndBarcodeAndDeletedAtIsNull(companyId, value)
                    .map(p -> p.getId().equals(excludeId))
                    .orElse(true);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getCategories() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return categoryRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(c -> CategoryDto.builder()
                        .id(c.getId())
                        .parentId(c.getParent() != null ? c.getParent().getId() : null)
                        .code(c.getCode())
                        .name(c.getName())
                        .description(c.getDescription())
                        .isActive(Boolean.TRUE.equals(c.getIsActive()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BrandDto> getBrands() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return brandRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(b -> BrandDto.builder()
                        .id(b.getId())
                        .name(b.getName())
                        .description(b.getDescription())
                        .isActive(Boolean.TRUE.equals(b.getIsActive()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UnitDto> getUnits() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return unitOfMeasureRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(u -> UnitDto.builder()
                        .id(u.getId())
                        .code(u.getCode())
                        .name(u.getName())
                        .symbol(u.getSymbol())
                        .isActive(Boolean.TRUE.equals(u.getIsActive()))
                        .build())
                .toList();
    }

    public ProductDto toDto(Product p) {
        BigDecimal totalStock = stockLevelRepository != null ? stockLevelRepository.getTotalAvailableStockByProductId(p.getId()) : BigDecimal.ZERO;
        if (totalStock == null) totalStock = BigDecimal.ZERO;
        boolean active = Boolean.TRUE.equals(p.getIsActive());
        String status = active ? "active" : "archived";
        String stockStatus = totalStock.compareTo(BigDecimal.ZERO) > 0 ? "in-stock" : "out-of-stock";

        return ProductDto.builder()
                .id(p.getId())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .brandId(p.getBrand() != null ? p.getBrand().getId() : null)
                .brandName(p.getBrand() != null ? p.getBrand().getName() : null)
                .unitId(p.getUnit() != null ? p.getUnit().getId() : null)
                .unitCode(p.getUnit() != null ? p.getUnit().getCode() : null)
                .unitName(p.getUnit() != null ? p.getUnit().getName() : null)
                .taxId(p.getTax() != null ? p.getTax().getId() : null)
                .taxRate(p.getTax() != null ? p.getTax().getRate() : BigDecimal.ZERO)
                .sku(p.getSku())
                .barcode(p.getBarcode())
                .name(p.getName())
                .description(p.getDescription())
                .costPrice(p.getCostPrice())
                .purchasePrice(p.getCostPrice())
                .sellingPrice(p.getSellingPrice())
                .salePrice(p.getSellingPrice())
                .minStockAlert(p.getMinStockAlert())
                .totalAvailableStock(totalStock)
                .totalStock(totalStock)
                .hasVariants(Boolean.TRUE.equals(p.getHasVariants()))
                .imageUrl(p.getImageUrl())
                .isActive(active)
                .status(status)
                .stockStatus(stockStatus)
                .createdAt(p.getCreatedAt())
                .build();
    }
}
