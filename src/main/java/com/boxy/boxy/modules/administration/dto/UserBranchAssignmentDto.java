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
    private Long branchId;
    private String branchName;
    private Long roleId;
    private String roleName;
    private boolean isDefault;
}
