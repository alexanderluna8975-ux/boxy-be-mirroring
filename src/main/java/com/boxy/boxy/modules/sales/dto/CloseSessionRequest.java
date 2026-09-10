package com.boxy.boxy.modules.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CloseSessionRequest {
    @NotNull(message = "Actual cash counted is required")
    private BigDecimal actualCash;

    private String notes;
}
