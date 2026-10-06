package com.boxy.boxy.modules.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** The note that goes with voiding a sale: required, so every voided sale says why. */
@Data
public class VoidSaleRequest {

    @NotBlank(message = "El motivo de la anulación es obligatorio")
    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    private String reason;
}
