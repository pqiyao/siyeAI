INSERT INTO app_runtime_setting (setting_key, setting_value, updated_at)
VALUES (
    'chat_runtime_default',
    '{"maxTokens":1000,"maxContext":65536}',
    CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    setting_key = setting_key;
