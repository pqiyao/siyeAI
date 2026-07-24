ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS tts_provider_source VARCHAR(32) NOT NULL DEFAULT '';

ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS tts_api_key_cipher TEXT NULL;

ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS tts_custom_url VARCHAR(512) NOT NULL DEFAULT '';

