CREATE TABLE IF NOT EXISTS app_admin_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_key VARCHAR(64) NOT NULL,
    role_name VARCHAR(120) NOT NULL,
    permissions_json TEXT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    built_in TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    remark VARCHAR(255) NULL,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_admin_role_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_admin_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    nick_name VARCHAR(120) NOT NULL,
    encoded_password VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    built_in TINYINT(1) NOT NULL DEFAULT 0,
    must_reset_password TINYINT(1) NOT NULL DEFAULT 0,
    last_login_at DATETIME NULL,
    last_login_ip VARCHAR(64) NULL,
    remark VARCHAR(255) NULL,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_admin_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_admin_account_role (
    account_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, role_id),
    CONSTRAINT fk_admin_account_role_account FOREIGN KEY (account_id) REFERENCES app_admin_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_account_role_role FOREIGN KEY (role_id) REFERENCES app_admin_role (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_app_admin_account_status ON app_admin_account (status);
CREATE INDEX idx_app_admin_role_enabled ON app_admin_role (enabled);
