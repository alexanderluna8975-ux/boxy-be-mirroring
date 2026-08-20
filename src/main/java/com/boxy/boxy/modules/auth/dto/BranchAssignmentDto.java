package com.boxy.boxy.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchAssignmentDto {
    private String branchId;
    private String branchCode;
    private String branchName;
    private String roleCode;
    private String roleName;
    private boolean isDefault;
}
