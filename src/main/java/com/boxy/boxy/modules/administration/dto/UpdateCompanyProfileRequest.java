package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanyProfileRequest {
    @NotBlank(message = "Company name is required")
    private String name;
    private String tradeName;
    private String slogan;
    private String taxId;
    private String email;
    private String phone;
    private String address;
    private String logoUrl;

    // Branding
    private String primaryColor;
    private String primaryHover;
    private String primarySubtleBg;

    // Localization
    private String currencyCode;
    private String currencySymbol;
    private String taxName;
    private BigDecimal defaultTaxRate;
    private String taxIdLabel;
    private String timezone;

    // Feature Flags
    private Boolean hasPos;
    private Boolean hasBatches;
    private Boolean hasVariants;
    private Boolean hasTransfers;
    private Boolean hasPurchasing;
    private Boolean hasQuotations;
    private Boolean hasMultiBranch;
    private String unitPrecision;

    // Terminology
    private String termProduct;
    private String termProducts;
    private String termInventory;
    private String termCustomer;
    private String termPos;
}
