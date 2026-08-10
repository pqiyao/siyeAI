ALTER TABLE app_user
    MODIFY telegram_user_id BIGINT NULL;

UPDATE app_user
SET telegram_user_id = NULL
WHERE telegram_user_id >= 9000000000
  AND (first_name = 'H5' OR username LIKE 'h5_%')
  AND EXISTS (
      SELECT 1
      FROM app_h5_client_uid bind
      WHERE bind.user_id = app_user.id
  );

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

CREATE INDEX IF NOT EXISTS idx_app_user_identity_user_id ON app_user_identity(user_id);

INSERT IGNORE INTO app_user_identity (user_id, identity_type, identity_key, credential_hash, verified, meta_json)
SELECT u.id, 'telegram', CAST(u.telegram_user_id AS CHAR), NULL, 1, NULL
FROM app_user u
WHERE u.telegram_user_id IS NOT NULL;
