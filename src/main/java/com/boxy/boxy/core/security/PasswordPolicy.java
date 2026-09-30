package com.boxy.boxy.core.security;

import com.boxy.boxy.core.exception.BusinessException;

/**
 * Shared across every place a password is set (user creation, admin reset, self-service change) —
 * length is already enforced per-DTO via {@code @Size(min = 10)}; this covers the one check that
 * needs the username alongside the password, which Bean Validation can't express on its own.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    public static void rejectIfMatchesUsername(String password, String username) {
        if (password != null && username != null && password.equalsIgnoreCase(username)) {
            throw new BusinessException("PASSWORD_MATCHES_USERNAME",
                    "The password must not be the same as the username.");
        }
    }
}
