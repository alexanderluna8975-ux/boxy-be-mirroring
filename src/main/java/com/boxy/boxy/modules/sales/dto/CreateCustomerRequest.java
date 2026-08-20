package com.boxy.boxy.modules.sales.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateCustomerRequest {
    @NotBlank(message = "Document type is required")
    private String documentType;

    @NotBlank(message = "Document number is required")
    private String documentNumber;

    @NotBlank(message = "Customer name is required")
    private String name;

    private String email;
    private String phone;
    private String address;
    private BigDecimal creditLimit = BigDecimal.ZERO;
}
