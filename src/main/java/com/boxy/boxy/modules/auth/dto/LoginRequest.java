package com.boxy.boxy.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username or email is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    /** The `g-recaptcha-response` token from the frontend's `grecaptcha.execute('login')` call —
     *  verified by {@code RecaptchaService} before the password is even checked. Not `@NotBlank`:
     *  a blank/missing token is a valid input that {@code RecaptchaService} rejects on its own
     *  (fails closed), so it can stay optional here for an environment where reCAPTCHA is
     *  disabled entirely (local dev). */
    private String recaptchaToken;
}
