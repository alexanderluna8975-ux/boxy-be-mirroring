package com.boxy.boxy.modules.administration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    private Long id;
    private Long userId;
    private String actorName;
    private String action;
    private String entity;
    private String entityLabel;
    private String resourceType;
    private String resourceId;
    private String previousValue;
    private String newValue;
    private String ipAddress;
    private String userAgent;
    private String details;
    private Instant createdAt;
    private String occurredAt;
}
