package com.boxy.boxy.modules.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OpenSessionRequest {
    @NotNull(message = "La sucursal es obligatoria")
    private Long branchId;

    @NotNull(message = "El monto inicial de caja es obligatorio")
    @DecimalMin(value = "0.0", message = "El monto inicial no puede ser negativo")
    private BigDecimal initialCash;

    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notes;
}
