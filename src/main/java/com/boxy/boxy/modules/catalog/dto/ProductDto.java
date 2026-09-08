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
    private Long id;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private Long unitId;
    private String unitCode;
    private Long taxId;
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
