package com.boxy.boxy.modules.catalog.entity;

import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.Tax;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitOfMeasure unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_id")
    private Tax tax;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(length = 100)
    private String barcode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cost_price", nullable = false, precision = 14, scale = 4)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    /** What was actually paid per unit on the most recent goods receipt — unlike {@code costPrice}
     *  (a weighted average blended with prior stock), this is the raw last-paid figure, and is what
     *  a new purchase order line's "Costo unitario" should default to. {@code null} until the first
     *  receipt; see {@code PurchasingService#receiveGoods}. */
    @Column(name = "last_purchase_cost", precision = 14, scale = 4)
    private BigDecimal lastPurchaseCost;

    @Column(name = "selling_price", nullable = false, precision = 14, scale = 4)
    @Builder.Default
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @Column(name = "min_stock_alert", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal minStockAlert = BigDecimal.ZERO;

    @Column(name = "has_variants", nullable = false)
    @Builder.Default
    private Boolean hasVariants = false;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public BigDecimal getSalePrice() {
        return this.sellingPrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.sellingPrice = salePrice;
    }

    public UnitOfMeasure getUnitOfMeasure() {
        return this.unit;
    }

    public void setUnitOfMeasure(UnitOfMeasure unit) {
        this.unit = unit;
    }
}
