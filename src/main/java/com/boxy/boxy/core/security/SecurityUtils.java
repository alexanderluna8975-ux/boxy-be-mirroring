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

    public static Long getCurrentUserId() {
        return getCurrentUser().map(UserPrincipal::getId).orElse(1L);
    }

    public static Long getCurrentCompanyId() {
        return getCurrentUser().map(UserPrincipal::getCompanyId).orElse(1L);
    }

    public static Long getCurrentBranchId() {
        return getCurrentUser().map(UserPrincipal::getActiveBranchId).orElse(1L);
    }
}
