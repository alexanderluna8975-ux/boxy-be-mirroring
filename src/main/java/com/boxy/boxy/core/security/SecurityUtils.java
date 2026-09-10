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

    /**
     * @deprecated Silently falls back to user 1 when there is no authenticated principal,
     * which lets unauthenticated calls masquerade as that user instead of failing. New code
     * should use {@link #requireCurrentUserId()}; this is kept only for call sites not yet
     * migrated off it.
     */
    @Deprecated
    public static Long getCurrentUserId() {
        return getCurrentUser().map(UserPrincipal::getId).orElse(1L);
    }

    /**
     * @deprecated Silently falls back to company 1 when there is no authenticated principal —
     * see {@link #getCurrentUserId()}. New code should use {@link #requireCurrentCompanyId()}.
     */
    @Deprecated
    public static Long getCurrentCompanyId() {
        return getCurrentUser().map(UserPrincipal::getCompanyId).orElse(1L);
    }

    /**
     * @deprecated Silently falls back to branch 1 when there is no authenticated principal —
     * see {@link #getCurrentUserId()}. New code should use {@link #requireCurrentBranchId()}.
     */
    @Deprecated
    public static Long getCurrentBranchId() {
        return getCurrentUser().map(UserPrincipal::getActiveBranchId).orElse(1L);
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
