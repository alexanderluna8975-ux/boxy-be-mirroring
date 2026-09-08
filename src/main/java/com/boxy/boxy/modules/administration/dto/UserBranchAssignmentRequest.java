package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserBranchAssignmentRequest {
    @NotBlank(message = "Branch ID is required")
    private Long branchId;

    @NotBlank(message = "Role ID is required")
    private Long roleId;

    private boolean isDefault;
}
