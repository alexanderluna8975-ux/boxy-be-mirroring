package com.boxy.boxy.modules.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePriceAdjustmentRequest {

    @NotBlank(message = "La tarifa es requerida")
    private String tariff;

    @NotBlank(message = "La unidad es requerida")
    private String unit;

    @NotNull(message = "El importe es requerido")
    @DecimalMin(value = "0", message = "El importe no puede ser negativo")
    private BigDecimal amount;

    @NotBlank(message = "\"Basado en\" es requerido")
    private String basedOn;

    @NotBlank(message = "El modo de redondeo es requerido")
    private String roundingMode;

    private String notes;

    @NotEmpty(message = "Debe seleccionar al menos un producto")
    private List<String> productIds;

    /** productId (same string shape as {@code productIds}) -> manual final price, for rows
     *  hand-edited after the bulk formula. A productId present here skips the formula for it. */
    private Map<String, BigDecimal> overrides;
}
