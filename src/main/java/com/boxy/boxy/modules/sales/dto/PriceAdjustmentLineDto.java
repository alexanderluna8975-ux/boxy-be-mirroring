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
public class PriceAdjustmentLineDto {
    private Long id;
    private Long productId;
    private String sku;
    private String productName;
    private BigDecimal purchasePrice;
    private BigDecimal previousSalePrice;
    private BigDecimal newSalePrice;
    private BigDecimal previousMarginPercent;
    private BigDecimal newMarginPercent;
    private boolean overridden;
}
