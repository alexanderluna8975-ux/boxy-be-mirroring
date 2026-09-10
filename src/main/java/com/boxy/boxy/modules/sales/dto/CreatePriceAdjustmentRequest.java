package com.boxy.boxy.modules.sales.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePriceAdjustmentRequest {

    @NotNull(message = "El margen porcentual es requerido")
    private BigDecimal marginPercent;

    private String notes;

    @NotEmpty(message = "Debe seleccionar al menos un producto")
    private List<String> productIds;
}
