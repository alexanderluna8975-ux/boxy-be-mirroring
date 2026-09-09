package com.boxy.boxy.modules.sales.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateCustomerRequest {
    private String documentType = "RFC";

    @JsonAlias({"taxId", "code", "documentNumber"})
    private String documentNumber;

    @NotBlank(message = "Customer name is required")
    private String name;

    private String type;
    private String email;
    private String phone;
    private String address;
    private Long branchId;
    private BigDecimal creditLimit = BigDecimal.ZERO;
}
