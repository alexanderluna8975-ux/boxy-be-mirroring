package com.boxy.boxy.core.realtime.events;

/** Published whenever an audit-log entry is written — i.e. for essentially any action in the system. */
public record AuditRecordedEvent(Long companyId, Long actorId, String action, String entityType,
                                 String resourceId, String entityLabel) {
}
