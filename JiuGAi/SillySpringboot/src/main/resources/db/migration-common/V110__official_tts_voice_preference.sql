ALTER TABLE app_h5_user_ai_provider
    ADD COLUMN official_tts_voice_name VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE app_h5_user_ai_provider
    ADD COLUMN official_tts_voice_template_code VARCHAR(64) NOT NULL DEFAULT '';
