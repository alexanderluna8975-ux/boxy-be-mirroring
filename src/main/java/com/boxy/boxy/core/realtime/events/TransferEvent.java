package com.boxy.boxy.core.realtime.events;

/**
 * A transfer changing state. {@code createdByUserId} is who requested it (the one to tell when it
 * is rejected); {@code actorId} is whoever performed this step — never notified about their own action.
 */
public record TransferEvent(
        Type type,
        Long companyId,
        Long transferId,
        String transferNumber,
        Long originBranchId,
        Long destinationBranchId,
        boolean hasLoss,
        Long createdByUserId,
        Long actorId) {

    public enum Type { REQUESTED, DISPATCHED, RECEIVED, REJECTED }
}
