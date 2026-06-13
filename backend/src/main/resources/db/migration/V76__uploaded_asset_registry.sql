CREATE TABLE IF NOT EXISTS app_uploaded_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    media_key VARCHAR(512) NOT NULL,
    storage_provider VARCHAR(20) NOT NULL DEFAULT 'local',
    media_type VARCHAR(20) NOT NULL DEFAULT 'file',
    source_module VARCHAR(32) NOT NULL DEFAULT 'h5_common',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_uploaded_asset_media_key (media_key),
    KEY idx_app_uploaded_asset_owner (owner_user_id, created_at),
    KEY idx_app_uploaded_asset_module (source_module, created_at)
);
