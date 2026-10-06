package com.boxy.boxy.core.realtime;

import java.security.Principal;
import java.util.Set;

/**
 * Who is on the other end of an authenticated WebSocket, attached at STOMP CONNECT. Deliberately
 * carries the tenant scope (company + assigned branches) so a SUBSCRIBE can be authorized without
 * a database hit per frame.
 * <p>
 * {@link #getName()} is the user id — not the username — because Spring routes user destinations
 * ({@code /user/queue/...}) by principal name, and an id is stable and unique where a username can
 * change. {@code NotificationService} addresses users with the same value.
 */
public final class RealtimePrincipal implements Principal {

    private final Long userId;
    private final Long companyId;
    private final Set<Long> branchIds;

    public RealtimePrincipal(Long userId, Long companyId, Set<Long> branchIds) {
        this.userId = userId;
        this.companyId = companyId;
        this.branchIds = Set.copyOf(branchIds);
    }

    public Long userId() {
        return userId;
    }

    public Long companyId() {
        return companyId;
    }

    public boolean hasBranch(Long branchId) {
        return branchIds.contains(branchId);
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
