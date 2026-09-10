package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** No `code`/`isSystem` — a role's code is its stable identity and the 4 system roles can't
 *  be renamed away from what the RBAC seed and `MANUAL_USUARIO.md` document. */
@Data
public class UpdateRoleRequest {
    @NotBlank(message = "Name is required")
    private String name;

    private String description;
}
