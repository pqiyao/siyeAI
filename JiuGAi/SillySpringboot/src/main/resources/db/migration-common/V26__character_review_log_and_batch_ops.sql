CREATE TABLE IF NOT EXISTS app_character_review_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    character_name VARCHAR(120) NOT NULL DEFAULT '',
    owner_user_id BIGINT NULL,
    owner_client_uid VARCHAR(64) NULL,
    review_status VARCHAR(16) NOT NULL,
    review_reason VARCHAR(500) NULL,
    operator_name VARCHAR(64) NOT NULL DEFAULT 'admin',
    batch_no VARCHAR(48) NULL,
    summary VARCHAR(255) NOT NULL DEFAULT '',
    detail_json MEDIUMTEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_character_review_log_created (created_at),
    KEY idx_character_review_log_character (character_id, created_at),
    KEY idx_character_review_log_owner (owner_user_id, created_at),
    KEY idx_character_review_log_status (review_status, created_at),
    KEY idx_character_review_log_batch (batch_no, created_at)
);

INSERT INTO app_character_review_log (
    character_id,
    character_name,
    owner_user_id,
    review_status,
    review_reason,
    operator_name,
    summary,
    detail_json,
    created_at
)
SELECT
    c.id,
    COALESCE(c.name, ''),
    c.owner_user_id,
    COALESCE(NULLIF(c.review_status, ''), 'APPROVED'),
    c.review_reason,
    COALESCE(NULLIF(c.reviewed_by, ''), 'system'),
    CONCAT(
        '历史审核结果：',
        CASE COALESCE(NULLIF(c.review_status, ''), 'APPROVED')
            WHEN 'REJECTED' THEN '已驳回'
            WHEN 'PENDING' THEN '待审核'
            ELSE '已通过'
        END,
        ' - ',
        COALESCE(c.name, '')
    ),
    '{}',
    c.reviewed_at
FROM app_character c
WHERE c.deleted_at IS NULL
  AND c.reviewed_at IS NOT NULL
  AND (SELECT COUNT(*) FROM app_character_review_log) = 0;
