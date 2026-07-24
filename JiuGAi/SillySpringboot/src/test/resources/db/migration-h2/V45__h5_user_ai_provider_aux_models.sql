ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS vision_model_name VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS audio_model_name VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS image_model_name VARCHAR(255) NOT NULL DEFAULT '';

