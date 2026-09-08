package com.boxy.boxy.modules.sales.dto;

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
public class CustomerDto {
    private Long id;
    private String documentType;
    private String documentNumber;
    private String name;
    private String email;
    private String phone;
    private String address;
    private BigDecimal creditLimit;
    private BigDecimal currentCredit;
    private boolean isActive;
    private Instant createdAt;
}
