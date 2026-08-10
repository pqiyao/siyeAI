CREATE TABLE app_h5_upload_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    asset_url VARCHAR(512) NOT NULL,
    relative_path VARCHAR(512) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_h5_upload_asset_url (asset_url),
    UNIQUE KEY uk_h5_upload_relative_path (relative_path),
    KEY idx_h5_upload_asset_owner (owner_user_id, created_at),
    CONSTRAINT fk_h5_upload_asset_owner
        FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
        ON DELETE RESTRICT
);
