package com.boxy.boxy.modules.notifications.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One notification for one recipient. Plain id columns (not entity references), like
 * {@code AuditLog}: a notification is an immutable record of something that happened, never
 * navigated through to the user/company objects.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(nullable = false, length = 40)
    private String type;

    /** {@code info} | {@code warning} | {@code danger} — the same vocabulary as the frontend's toasts. */
    @Column(nullable = false, length = 10)
    private String severity;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "resource_type", length = 40)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    /** In-app route to open when the notification is clicked, e.g. {@code /inventory/transfers/9}. */
    @Column(length = 255)
    private String link;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
