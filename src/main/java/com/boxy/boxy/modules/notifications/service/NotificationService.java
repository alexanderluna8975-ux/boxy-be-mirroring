package com.boxy.boxy.modules.notifications.service;

import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.realtime.RealtimeMessage;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.notifications.dto.NotificationDto;
import com.boxy.boxy.modules.notifications.entity.Notification;
import com.boxy.boxy.modules.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Creates, delivers and manages in-app notifications. Every read and write of an existing
 * notification is scoped to the <i>current user</i> — there is no way to address another user's
 * notification by id (asking for one is a 404, same as "doesn't exist").
 * <p>
 * Creation is best-effort like {@code AuditLogService}: it is always triggered from an
 * after-commit listener, i.e. the business operation already succeeded, so a failure here is logged
 * and never allowed to surface as an error to the user who just did the work.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Where a notification goes on the WebSocket — resolved by Spring against each recipient's own session. */
    public static final String USER_QUEUE = "/queue/notifications";
    /** The envelope {@code type} of a pushed notification (its own {@code type} lives inside {@code data}). */
    public static final String EVENT_TYPE = "NOTIFICATION";

    private static final int TITLE_MAX = 150;
    private static final int MESSAGE_MAX = 500;

    /** What to say, independent of who hears it. */
    public record Spec(String type, String severity, String title, String message,
                       String resourceType, String resourceId, String link) {}

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PlatformTransactionManager transactionManager;

    /**
     * Stores one notification per recipient, then pushes each to that user if they are connected.
     * The rows are written in a transaction of their own ({@code REQUIRES_NEW}: this runs after the
     * publishing transaction has already committed) and committed <i>before</i> anything is pushed,
     * so a client that receives a notification can immediately mark it read without racing the insert.
     */
    public void notifyUsers(Long companyId, Collection<Long> recipientUserIds, Spec spec) {
        Set<Long> recipients = recipientUserIds == null ? Set.of() : Set.copyOf(recipientUserIds);
        if (recipients.isEmpty()) {
            return;
        }

        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            List<Notification> saved = template.execute(status -> notificationRepository.saveAll(
                    recipients.stream().map(userId -> toEntity(companyId, userId, spec)).toList()));

            if (saved != null) {
                saved.forEach(this::push);
            }
        } catch (Exception e) {
            log.warn("Failed to create '{}' notification for {} recipient(s): {}",
                    spec.type(), recipients.size(), e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(boolean unreadOnly, Pageable pageable) {
        Long userId = SecurityUtils.requireCurrentUserId();
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(NotificationService::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(SecurityUtils.requireCurrentUserId());
    }

    @Transactional
    public NotificationDto markRead(Long id) {
        Notification notification = notificationRepository
                .findByIdAndRecipientUserId(id, SecurityUtils.requireCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return toDto(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(SecurityUtils.requireCurrentUserId(), Instant.now());
    }

    /** Housekeeping: read notifications are only history, so old ones are dropped. Unread ones are kept indefinitely. */
    @Transactional
    public int purgeReadOlderThan(Duration age) {
        return notificationRepository.deleteReadBefore(Instant.now().minus(age));
    }

    private void push(Notification notification) {
        try {
            // Same envelope as every other real-time message, so the frontend parses them all alike.
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(notification.getRecipientUserId()), USER_QUEUE,
                    RealtimeMessage.of(EVENT_TYPE, toDto(notification)));
        } catch (Exception e) {
            // The row is stored; the user will see it from the REST list even if the push failed.
            log.warn("Failed to push notification {} to user {}: {}",
                    notification.getId(), notification.getRecipientUserId(), e.getMessage());
        }
    }

    private static Notification toEntity(Long companyId, Long userId, Spec spec) {
        return Notification.builder()
                .companyId(companyId)
                .recipientUserId(userId)
                .type(spec.type())
                .severity(spec.severity())
                .title(truncate(spec.title(), TITLE_MAX))
                .message(truncate(spec.message(), MESSAGE_MAX))
                .resourceType(spec.resourceType())
                .resourceId(spec.resourceId())
                .link(spec.link())
                .build();
    }

    static NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType())
                .severity(n.getSeverity())
                .title(n.getTitle())
                .message(n.getMessage())
                .resourceType(n.getResourceType())
                .resourceId(n.getResourceId())
                .link(n.getLink())
                .read(n.getReadAt() != null)
                .createdAt((n.getCreatedAt() != null ? n.getCreatedAt() : Instant.now()).toString())
                .build();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
