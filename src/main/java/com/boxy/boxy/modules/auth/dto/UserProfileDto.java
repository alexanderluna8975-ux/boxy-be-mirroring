package com.boxy.boxy.modules.auth.dto;

import com.boxy.boxy.modules.administration.dto.UserPermissionOverridesDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private String status;
    private Long activeBranchId;
    private List<BranchAssignmentDto> branches;
    private List<String> permissions;
    private UserPermissionOverridesDto permissionOverrides;
}
