ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS stt_model_name VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS tts_model_name VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS tts_voice_name VARCHAR(64) NOT NULL DEFAULT '';

UPDATE app_h5_user_ai_provider
SET
    stt_model_name = CASE
        WHEN TRIM(stt_model_name) = '' THEN audio_model_name
        ELSE stt_model_name
    END,
    tts_model_name = CASE
        WHEN TRIM(tts_model_name) = '' THEN audio_model_name
        ELSE tts_model_name
    END
WHERE TRIM(audio_model_name) <> '';
