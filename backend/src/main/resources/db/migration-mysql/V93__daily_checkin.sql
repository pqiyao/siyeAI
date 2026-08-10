-- V93: daily check-in activity + claim + daily quota bonus

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_h5_user_profile_ext'
              AND column_name = 'daily_chat_bonus'
        ),
        'SELECT 1',
        'ALTER TABLE app_h5_user_profile_ext ADD COLUMN daily_chat_bonus INT NOT NULL DEFAULT 0 AFTER daily_chat_used'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_h5_user_profile_ext'
              AND column_name = 'daily_image_bonus'
        ),
        'SELECT 1',
        'ALTER TABLE app_h5_user_profile_ext ADD COLUMN daily_image_bonus INT NOT NULL DEFAULT 0 AFTER daily_image_used'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS app_checkin_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    start_at DATETIME NULL,
    end_at DATETIME NULL,
    audience VARCHAR(32) NOT NULL DEFAULT 'ALL_LOGIN',
    reward_score INT NOT NULL DEFAULT 0,
    reward_gold INT NOT NULL DEFAULT 0,
    reward_chat_bonus INT NOT NULL DEFAULT 0,
    reward_image_bonus INT NOT NULL DEFAULT 0,
    streak_rules_json TEXT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    note VARCHAR(512) NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_checkin_activity_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_checkin_claim (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    biz_date DATE NOT NULL,
    streak_day INT NOT NULL DEFAULT 1,
    reward_score INT NOT NULL DEFAULT 0,
    reward_gold INT NOT NULL DEFAULT 0,
    reward_chat_bonus INT NOT NULL DEFAULT 0,
    reward_image_bonus INT NOT NULL DEFAULT 0,
    reward_json TEXT NULL,
    ledger_idempotency_key VARCHAR(96) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_checkin_claim_user_activity_day (user_id, activity_id, biz_date),
    KEY idx_checkin_claim_activity_day (activity_id, biz_date),
    KEY idx_checkin_claim_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO app_checkin_activity (
    code, name, enabled, audience,
    reward_score, reward_gold, reward_chat_bonus, reward_image_bonus,
    streak_rules_json, timezone, note
) VALUES (
    'daily_checkin',
    '每日签到',
    1,
    'ALL_LOGIN',
    10,
    0,
    2,
    0,
    '[{"day":3,"score":5,"gold":0,"chatBonus":0,"imageBonus":0},{"day":7,"score":20,"gold":0,"chatBonus":1,"imageBonus":0}]',
    'Asia/Shanghai',
    '默认每日签到：偏钻石，少量今日聊天次数；第3/7天加赠'
) ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    note = VALUES(note);
