CREATE TABLE IF NOT EXISTS app_tag_library (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    category VARCHAR(64) NULL,
    color VARCHAR(32) NULL,
    vip_only TINYINT(1) NOT NULL DEFAULT 0,
    adult_only TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_tag_library_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS app_runtime_setting (
    setting_key VARCHAR(64) PRIMARY KEY,
    setting_value LONGTEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

ALTER TABLE app_h5_user_profile_ext ADD COLUMN IF NOT EXISTS chat_quota_override INT NULL;

ALTER TABLE app_h5_user_profile_ext ADD COLUMN IF NOT EXISTS image_quota_override INT NULL;

ALTER TABLE app_h5_user_profile_ext ADD COLUMN IF NOT EXISTS daily_chat_used INT NOT NULL DEFAULT 0;

ALTER TABLE app_h5_user_profile_ext ADD COLUMN IF NOT EXISTS daily_image_used INT NOT NULL DEFAULT 0;

ALTER TABLE app_h5_user_profile_ext ADD COLUMN IF NOT EXISTS usage_reset_date DATE NULL;

INSERT INTO app_runtime_setting (setting_key, setting_value)
SELECT
    'entitlement_policy',
    '{
      "guestDailyChatQuota":20,
      "vipDailyChatQuota":80,
      "svipDailyChatQuota":200,
      "guestDailyImageQuota":0,
      "vipDailyImageQuota":5,
      "svipDailyImageQuota":30,
      "guestCanAccessVipCharacters":false,
      "vipCanAccessVipCharacters":true,
      "svipCanAccessVipCharacters":true,
      "continueConsumesQuota":true,
      "regenerateConsumesQuota":true
    }'
WHERE NOT EXISTS (
    SELECT 1
    FROM app_runtime_setting
    WHERE setting_key = 'entitlement_policy'
);
