package com.boxy.boxy.modules.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CloseSessionRequest {
    @NotNull(message = "El efectivo contado es obligatorio")
    @DecimalMin(value = "0.0", message = "El efectivo contado no puede ser negativo")
    private BigDecimal actualCash;

    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notes;
}
