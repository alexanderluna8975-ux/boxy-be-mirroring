package com.boxy.boxy.modules.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OpenSessionRequest {
    @NotBlank(message = "Branch ID is required")
    private Long branchId;

    @NotNull(message = "Initial cash amount is required")
    @DecimalMin(value = "0.0", message = "Initial cash must be >= 0")
    private BigDecimal initialCash;

    private String notes;
}
