ALTER TABLE app_user
    MODIFY telegram_user_id BIGINT NULL;

UPDATE app_user u
JOIN app_h5_client_uid bind ON bind.user_id = u.id
SET u.telegram_user_id = NULL
WHERE u.telegram_user_id >= 9000000000
  AND (u.first_name = 'H5' OR u.username LIKE 'h5_%');

CREATE TABLE IF NOT EXISTS app_user_identity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    identity_type VARCHAR(32) NOT NULL,
    identity_key VARCHAR(128) NOT NULL,
    credential_hash VARCHAR(255) NULL,
    verified TINYINT NOT NULL DEFAULT 1,
    meta_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_user_identity_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT uk_app_user_identity_type_key UNIQUE (identity_type, identity_key)
);

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'app_user_identity'
      AND index_name = 'idx_app_user_identity_user_id'
);
SET @create_idx_sql := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_app_user_identity_user_id ON app_user_identity(user_id)',
    'SELECT 1'
);
PREPARE stmt_idx FROM @create_idx_sql;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;

INSERT IGNORE INTO app_user_identity (user_id, identity_type, identity_key, credential_hash, verified, meta_json)
SELECT u.id, 'telegram', CAST(u.telegram_user_id AS CHAR), NULL, 1, NULL
FROM app_user u
WHERE u.telegram_user_id IS NOT NULL;
