package com.boxy.boxy.modules.administration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One row of the permission matrix: a role and the raw permission codes it holds. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionsDto {
    private Long roleId;
    private String roleCode;
    private String roleName;

    @JsonProperty("isSystem")
    private boolean isSystem;

    private List<String> permissions;
}
