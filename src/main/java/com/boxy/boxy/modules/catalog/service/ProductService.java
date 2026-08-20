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

    @Transactional(readOnly = true)
    public Page<ProductDto> getProducts(String search, String categoryId, String brandId, Boolean isActive, Pageable pageable) {
        String companyId = SecurityUtils.getCurrentCompanyId();
        return productRepository.findAllFiltered(companyId, search, categoryId, brandId, isActive, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(String id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return toDto(product);
    }

    @Transactional
    public ProductDto createProduct(CreateProductRequest request) {
        String companyId = SecurityUtils.getCurrentCompanyId();
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

    @Transactional(readOnly = true)
    public List<CategoryDto> getCategories() {
        String companyId = SecurityUtils.getCurrentCompanyId();
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
        String companyId = SecurityUtils.getCurrentCompanyId();
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
        String companyId = SecurityUtils.getCurrentCompanyId();
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
        return ProductDto.builder()
                .id(p.getId())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .brandId(p.getBrand() != null ? p.getBrand().getId() : null)
                .brandName(p.getBrand() != null ? p.getBrand().getName() : null)
                .unitId(p.getUnit().getId())
                .unitCode(p.getUnit().getCode())
                .taxId(p.getTax() != null ? p.getTax().getId() : null)
                .taxRate(p.getTax() != null ? p.getTax().getRate() : BigDecimal.ZERO)
                .sku(p.getSku())
                .barcode(p.getBarcode())
                .name(p.getName())
                .description(p.getDescription())
                .costPrice(p.getCostPrice())
                .sellingPrice(p.getSellingPrice())
                .minStockAlert(p.getMinStockAlert())
                .hasVariants(Boolean.TRUE.equals(p.getHasVariants()))
                .imageUrl(p.getImageUrl())
                .isActive(Boolean.TRUE.equals(p.getIsActive()))
                .createdAt(p.getCreatedAt())
                .build();
    }
}
