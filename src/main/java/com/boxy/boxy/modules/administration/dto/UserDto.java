package com.boxy.boxy.modules.administration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private String status;
    private List<UserBranchAssignmentDto> branchAssignments;
    private UserPermissionOverridesDto permissionOverrides;
    private List<String> permissions;
    private Instant createdAt;
    private Instant updatedAt;
}
