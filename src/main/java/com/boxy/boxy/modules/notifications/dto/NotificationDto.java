package com.boxy.boxy.modules.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What the frontend gets, both from REST and pushed live on {@code /user/queue/notifications}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String type;
    private String severity;
    private String title;
    private String message;
    private String resourceType;
    private String resourceId;
    private String link;
    private boolean read;
    /** ISO-8601 instant — built as a string so it never depends on Jackson's date settings. */
    private String createdAt;
}
