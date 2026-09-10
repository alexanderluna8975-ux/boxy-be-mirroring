package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDto {
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Tax ID is required")
    private String taxId;

    private String email;
    private String phone;
    private String address;
    private String logoUrl;

    @NotBlank(message = "Currency code is required")
    private String currencyCode;

    @NotBlank(message = "Currency symbol is required")
    private String currencySymbol;

    @NotBlank(message = "Timezone is required")
    private String timezone;

    private boolean allowNegativeStock;

    private Instant createdAt;
    private Instant updatedAt;
}
