package com.boxy.boxy.modules.administration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserBranchAssignmentRequest {
    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotNull(message = "Role ID is required")
    private Long roleId;

    // See BranchDto for why @JsonProperty is needed on a Lombok-generated isX() getter —
    // without it, the getter's implied property name ("default") and the setter's ("isDefault")
    // disagree, and this field silently never got the incoming JSON value.
    @JsonProperty("isDefault")
    private boolean isDefault;
}
