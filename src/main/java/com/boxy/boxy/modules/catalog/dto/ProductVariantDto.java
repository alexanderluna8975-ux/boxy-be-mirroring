package com.boxy.boxy.modules.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantDto {
    private String id;
    private String sku;
    private String barcode;
    private String attributes;
    private BigDecimal costAdjustment;
    private BigDecimal priceAdjustment;
    private boolean isActive;
}
