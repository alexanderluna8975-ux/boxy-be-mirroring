package com.boxy.boxy.modules.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    private String unitName;
    private Long taxId;
    private BigDecimal taxRate;
    private String sku;
    private String barcode;
    private String name;
    private String description;
    private BigDecimal costPrice;
    private BigDecimal purchasePrice;
    /** What was actually paid per unit on the most recent goods receipt — see
     *  {@code Product#lastPurchaseCost}. Falls back to {@code costPrice} until the product has ever
     *  been received. */
    private BigDecimal lastPurchaseCost;
    private BigDecimal sellingPrice;
    private BigDecimal salePrice;
    private BigDecimal minStockAlert;
    private BigDecimal totalAvailableStock;
    private BigDecimal totalStock;
    private boolean hasVariants;
    private String imageUrl;
    private boolean isActive;
    private String status;
    private String stockStatus;
    /** Available stock split by branch (only branches where the product has stock > 0). */
    @Builder.Default
    private List<BranchStockDto> stockByBranch = new ArrayList<>();
    private List<ProductVariantDto> variants;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchStockDto {
        private Long branchId;
        private String branchCode;
        private String branchName;
        private BigDecimal quantity;
    }
}
