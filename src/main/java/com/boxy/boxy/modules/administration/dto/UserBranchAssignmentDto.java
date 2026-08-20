package com.boxy.boxy.modules.administration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBranchAssignmentDto {
    private String branchId;
    private String branchName;
    private String roleId;
    private String roleName;
    private boolean isDefault;
}
