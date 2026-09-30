package com.boxy.boxy.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Self-service password change — requires the current password, unlike an admin reset. */
@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 10, message = "Password must be at least 10 characters long")
    private String newPassword;
}
