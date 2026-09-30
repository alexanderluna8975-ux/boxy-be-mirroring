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

    /** Throws instead of masquerading as another user when there is no authenticated principal. */
    public static Long requireCurrentUserId() {
        return getCurrentUser().map(UserPrincipal::getId)
                .orElseThrow(() -> new IllegalStateException("No authenticated user in the current security context."));
    }

    public static Long requireCurrentCompanyId() {
        return getCurrentUser().map(UserPrincipal::getCompanyId)
                .orElseThrow(() -> new IllegalStateException("No authenticated user in the current security context."));
    }

    public static Long requireCurrentBranchId() {
        return getCurrentUser().map(UserPrincipal::getActiveBranchId)
                .orElseThrow(() -> new IllegalStateException("No authenticated user in the current security context."));
    }
}
