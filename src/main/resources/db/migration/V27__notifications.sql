-- In-app notifications: one row PER RECIPIENT, so "read" is a per-user fact with no join table.
-- Written by NotificationService when a domain event (low stock, transfer state change, voided
-- sale) matters to specific people, and also pushed live over the WebSocket to whoever is online;
-- this table is what lets someone who was offline see it later.
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT UNSIGNED NOT NULL,
    recipient_user_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(40) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    resource_type VARCHAR(40) NULL,
    resource_id VARCHAR(64) NULL,
    link VARCHAR(255) NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_notifications_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The two access paths: the unread badge / unread-only list, and the newest-first history.
CREATE INDEX idx_notifications_recipient_read ON notifications (recipient_user_id, read_at);
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_user_id, created_at);
