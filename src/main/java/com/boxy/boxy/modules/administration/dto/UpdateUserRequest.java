package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Deliberately has no {@code password} field — resetting a user's password is a separate,
 * more sensitive action (see {@code UserController.resetPassword}), not something that
 * should slip through as a side effect of an unrelated profile edit.
 */
@Data
public class UpdateUserRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String firstName;
    private String lastName;
    private String avatarUrl;

    @Valid
    private List<UserBranchAssignmentRequest> branchAssignments;
}
