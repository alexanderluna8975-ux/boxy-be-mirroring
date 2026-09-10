package com.boxy.boxy.modules.administration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    // See BranchDto for why @JsonProperty is needed on a Lombok-generated isX() getter.
    @JsonProperty("isDefault")
    private boolean isDefault;
}
