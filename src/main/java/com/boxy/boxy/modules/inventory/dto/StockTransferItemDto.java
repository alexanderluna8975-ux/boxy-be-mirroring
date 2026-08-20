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
    private String id;
    private String productId;
    private String productSku;
    private String productName;
    private BigDecimal quantityRequested;
    private BigDecimal quantityReceived;
}
