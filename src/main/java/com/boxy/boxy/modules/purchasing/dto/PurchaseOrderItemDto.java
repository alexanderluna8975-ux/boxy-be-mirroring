package com.boxy.boxy.modules.purchasing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItemDto {
    private Long id;
    private Long productId;
    private String productSku;
    private String sku;
    private String productName;
    private String name;
    private BigDecimal quantityOrdered;
    private BigDecimal quantityReceived;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal unitPrice;
    /** The product's current sale price (`Product.sellingPrice`), live — not a PO-owned field.
     *  Editable inline while the order is pending approval; see `PurchasingService#updatePurchaseOrderLine`. */
    private BigDecimal salePrice;
    private BigDecimal taxRate;
    private BigDecimal totalCost;
    private BigDecimal lineTotal;
}
