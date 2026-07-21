INSERT INTO app_runtime_setting (setting_key, setting_value, updated_at)
VALUES (
    'entitlement_policy',
    '{"guestDailyChatQuota":100,"vipDailyChatQuota":300,"svipDailyChatQuota":1000,"guestDailyImageQuota":0,"vipDailyImageQuota":5,"svipDailyImageQuota":30,"guestCanAccessVipCharacters":false,"vipCanAccessVipCharacters":true,"svipCanAccessVipCharacters":true,"continueConsumesQuota":false,"regenerateConsumesQuota":false}',
    NOW()
)
ON DUPLICATE KEY UPDATE
    setting_value = CASE
        WHEN setting_value = '{"guestDailyChatQuota":20,"vipDailyChatQuota":80,"svipDailyChatQuota":200,"guestDailyImageQuota":0,"vipDailyImageQuota":5,"svipDailyImageQuota":30,"guestCanAccessVipCharacters":false,"vipCanAccessVipCharacters":true,"svipCanAccessVipCharacters":true,"continueConsumesQuota":true,"regenerateConsumesQuota":true}'
            THEN VALUES(setting_value)
        ELSE setting_value
    END,
    updated_at = CASE
        WHEN setting_value = '{"guestDailyChatQuota":20,"vipDailyChatQuota":80,"svipDailyChatQuota":200,"guestDailyImageQuota":0,"vipDailyImageQuota":5,"svipDailyImageQuota":30,"guestCanAccessVipCharacters":false,"vipCanAccessVipCharacters":true,"svipCanAccessVipCharacters":true,"continueConsumesQuota":true,"regenerateConsumesQuota":true}'
            THEN NOW()
        ELSE updated_at
    END;
