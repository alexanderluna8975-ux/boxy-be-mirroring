package com.boxy.boxy.modules.administration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDto {
    private Long id;
    private String code;
    private String name;
    private String label;
    private String description;

    // See BranchDto for why @JsonProperty is needed on a Lombok-generated isX() getter.
    @JsonProperty("isSystem")
    private boolean isSystem;

    private Long userCount;
    private List<PermissionDto> permissions;
}
