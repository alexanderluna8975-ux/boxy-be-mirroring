package com.boxy.boxy.modules.administration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Admin-initiated password reset for another user — see {@code UserController.resetPassword}. */
@Data
public class ResetPasswordRequest {
    @NotBlank(message = "Password is required")
    @Size(min = 10, message = "Password must be at least 10 characters long")
    private String newPassword;
}
