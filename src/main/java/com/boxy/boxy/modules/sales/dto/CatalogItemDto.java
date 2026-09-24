package com.boxy.boxy.modules.sales.dto;

import com.boxy.boxy.modules.catalog.dto.ProductDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogItemDto {
    private Long productId;
    private String sku;
    private String barcode;
    private String name;
    private BigDecimal salePrice;
    private BigDecimal availableStock;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private Long unitId;
    private String unitName;
    private String imageUrl;
    private List<ProductDto.BranchStockDto> stockByBranch;
}
