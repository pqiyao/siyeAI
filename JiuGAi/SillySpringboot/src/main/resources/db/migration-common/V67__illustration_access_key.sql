CREATE TABLE IF NOT EXISTS app_illustration_access_key (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    access_code VARCHAR(32) NOT NULL,
    content_level VARCHAR(20) NOT NULL DEFAULT 'R18',
    expires_at DATETIME NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    max_uses INT NULL,
    used_count INT NOT NULL DEFAULT 0,
    note VARCHAR(255) NULL,
    created_by VARCHAR(120) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_illustration_access_key_code (access_code),
    KEY idx_app_illustration_access_key_active_expires (active, expires_at),
    KEY idx_app_illustration_access_key_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
