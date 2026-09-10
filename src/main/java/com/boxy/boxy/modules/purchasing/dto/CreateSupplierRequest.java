package com.boxy.boxy.modules.purchasing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSupplierRequest {
    /** Optional — the supplier form no longer captures the RFC. */
    private String taxId;

    @NotBlank(message = "Supplier name is required")
    private String name;

    private String contactName;
    private String email;
    private String phone;
    private String address;
    private int paymentTermsDays = 0;
}
