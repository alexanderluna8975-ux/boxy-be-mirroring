package com.boxy.boxy.modules.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Drops read notifications past their retention — unread ones are never deleted. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanup {

    private final NotificationService notificationService;

    @Value("${app.notifications.read-retention-days:90}")
    private long retentionDays;

    @Scheduled(cron = "${app.notifications.cleanup-cron:0 30 3 * * *}")
    public void purgeOldReadNotifications() {
        try {
            int deleted = notificationService.purgeReadOlderThan(Duration.ofDays(retentionDays));
            if (deleted > 0) {
                log.info("Purged {} read notification(s) older than {} days", deleted, retentionDays);
            }
        } catch (Exception e) {
            log.warn("Notification cleanup failed: {}", e.getMessage(), e);
        }
    }
}
