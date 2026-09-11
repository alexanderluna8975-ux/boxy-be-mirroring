package com.boxy.boxy.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    private String brandName;
    private String unitName;
}
