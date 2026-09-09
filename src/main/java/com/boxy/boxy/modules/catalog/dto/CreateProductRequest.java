package com.boxy.boxy.modules.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductRequest {
    private Long categoryId;
    private Long brandId;

    @NotNull(message = "Unit of measure ID is required")
    @com.fasterxml.jackson.annotation.JsonAlias({"unitId", "unitOfMeasureId"})
    private Long unitId;

    public Long getUnitOfMeasureId() {
        return unitId;
    }
    public void setUnitOfMeasureId(Long unitOfMeasureId) {
        this.unitId = unitOfMeasureId;
    }

    private Long taxId;

    @NotBlank(message = "SKU is required")
    private String sku;

    private String barcode;

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull(message = "Cost price is required")
    @DecimalMin(value = "0.0", message = "Cost price must be >= 0")
    private BigDecimal costPrice;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.0", message = "Selling price must be >= 0")
    private BigDecimal sellingPrice;

    private BigDecimal minStockAlert = BigDecimal.ZERO;
    private String imageUrl;
}
