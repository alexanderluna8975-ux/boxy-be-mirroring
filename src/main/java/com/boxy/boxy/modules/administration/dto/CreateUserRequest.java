package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateUserRequest {
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String password;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private List<UserBranchAssignmentRequest> branchAssignments;
}
