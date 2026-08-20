package com.boxy.boxy.modules.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private String id;
    private String categoryId;
    private String categoryName;
    private String brandId;
    private String brandName;
    private String unitId;
    private String unitCode;
    private String taxId;
    private BigDecimal taxRate;
    private String sku;
    private String barcode;
    private String name;
    private String description;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private BigDecimal minStockAlert;
    private BigDecimal totalAvailableStock;
    private boolean hasVariants;
    private String imageUrl;
    private boolean isActive;
    private List<ProductVariantDto> variants;
    private Instant createdAt;
}
