package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** A full replacement of a role's permission set — not a delta. */
@Data
public class UpdateRolePermissionsRequest {
    @NotEmpty(message = "permissions is required")
    private List<String> permissions;
}
