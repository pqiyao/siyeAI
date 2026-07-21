CREATE TABLE IF NOT EXISTS app_admin_permission_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NULL,
    target_key VARCHAR(120) NULL,
    target_name VARCHAR(120) NULL,
    action VARCHAR(64) NOT NULL,
    operator VARCHAR(64) NULL,
    change_summary VARCHAR(500) NULL,
    before_json TEXT NULL,
    after_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_admin_perm_log_created ON app_admin_permission_change_log (created_at);
CREATE INDEX idx_admin_perm_log_target ON app_admin_permission_change_log (target_type, target_id);
CREATE INDEX idx_admin_perm_log_action ON app_admin_permission_change_log (action);
CREATE INDEX idx_admin_perm_log_operator ON app_admin_permission_change_log (operator);
