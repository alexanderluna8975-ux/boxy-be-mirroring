package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "Name is required")
    private String name;

    private String description;
}
