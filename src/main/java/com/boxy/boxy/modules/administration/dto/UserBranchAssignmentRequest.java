package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserBranchAssignmentRequest {
    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotNull(message = "Role ID is required")
    private Long roleId;

    private boolean isDefault;
}
