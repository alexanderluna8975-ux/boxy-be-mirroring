package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserBranchAssignmentRequest {
    @NotBlank(message = "Branch ID is required")
    private String branchId;

    @NotBlank(message = "Role ID is required")
    private String roleId;

    private boolean isDefault;
}
