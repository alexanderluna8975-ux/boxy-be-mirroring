-- VARCHAR(64), not CHAR(64), to match every other hash column's JPA mapping in this schema (e.g.
-- refresh_tokens.token_hash) — a plain `@Column(length = 64)` String field maps to VARCHAR, and a
-- CHAR column here would fail Hibernate's schema validation at startup.
CREATE TABLE user_devices (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    device_id_hash VARCHAR(64) NOT NULL,
    fingerprint_hash VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    ip VARCHAR(64) NULL,
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_user_devices_user_device UNIQUE (user_id, device_id_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_user_devices_user_id ON user_devices (user_id);

-- Nullable: refresh tokens issued before this deploy have no device binding and are accepted
-- without it (see RefreshTokenService.rotate) — a token only becomes bound at its next rotation.
ALTER TABLE refresh_tokens
    ADD COLUMN device_id_hash VARCHAR(64) NULL,
    ADD COLUMN fingerprint_hash VARCHAR(64) NULL;
