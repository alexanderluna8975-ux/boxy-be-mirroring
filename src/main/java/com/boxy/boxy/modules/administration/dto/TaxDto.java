package com.boxy.boxy.modules.administration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxDto {
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    @DecimalMin(value = "0.0", message = "Rate must be >= 0")
    @DecimalMax(value = "1.0", message = "Rate must be a fraction between 0 and 1 (e.g. 0.18 for 18%)")
    private BigDecimal rate;

    // See BranchDto for why @JsonProperty is needed on Lombok-generated isX() getters.
    @JsonProperty("isDefault")
    private boolean isDefault;
    @JsonProperty("isActive")
    private boolean isActive;

    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
