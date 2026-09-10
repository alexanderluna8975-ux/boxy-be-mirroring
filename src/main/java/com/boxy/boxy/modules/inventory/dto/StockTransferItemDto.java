package com.boxy.boxy.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferItemDto {
    private Long id;
    private Long productId;
    private String productSku;
    private String sku;
    private String productName;
    private BigDecimal quantityRequested;
    private BigDecimal quantityReceived;
    private BigDecimal quantity;
}
