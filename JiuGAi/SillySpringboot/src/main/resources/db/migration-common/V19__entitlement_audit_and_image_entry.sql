CREATE TABLE IF NOT EXISTS app_entitlement_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scope_type VARCHAR(32) NOT NULL,
    action_type VARCHAR(48) NOT NULL,
    operator_type VARCHAR(16) NOT NULL DEFAULT 'SYSTEM',
    operator_name VARCHAR(64) NOT NULL DEFAULT '',
    target_user_id BIGINT NULL,
    client_uid VARCHAR(64) NULL,
    order_no VARCHAR(64) NULL,
    summary VARCHAR(255) NOT NULL DEFAULT '',
    detail_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_entitlement_audit_scope (scope_type, action_type, id),
    KEY idx_entitlement_audit_user (target_user_id, id),
    KEY idx_entitlement_audit_client (client_uid, id),
    KEY idx_entitlement_audit_order (order_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
