CREATE TABLE app_xiaoye_role_card_draft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    draft_key VARCHAR(64) NOT NULL,
    owner_admin_id BIGINT NOT NULL,
    payload_json LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_xiaoye_role_card_draft_key (draft_key),
    INDEX idx_xiaoye_role_card_draft_owner (owner_admin_id, status, expires_at),
    INDEX idx_xiaoye_role_card_draft_expiry (status, expires_at)
);
