package com.boxy.boxy.modules.sales.entity;

import com.boxy.boxy.modules.catalog.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "price_adjustment_lines")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAdjustmentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_adjustment_id", nullable = false)
    private PriceAdjustment priceAdjustment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "purchase_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal purchasePrice;

    @Column(name = "previous_sale_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal previousSalePrice;

    @Column(name = "new_sale_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal newSalePrice;

    @Column(name = "previous_margin_percent", precision = 6, scale = 2)
    private BigDecimal previousMarginPercent;

    @Column(name = "margin_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal marginPercent;
}
