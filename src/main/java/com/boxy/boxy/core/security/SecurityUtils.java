package com.boxy.boxy.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UserPrincipal> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static String getCurrentUserId() {
        return getCurrentUser().map(UserPrincipal::getId).orElse("SYSTEM");
    }

    public static String getCurrentCompanyId() {
        return getCurrentUser().map(UserPrincipal::getCompanyId).orElse("c0000000-0000-0000-0000-000000000001");
    }

    public static String getCurrentBranchId() {
        return getCurrentUser().map(UserPrincipal::getActiveBranchId).orElse(null);
    }
}
