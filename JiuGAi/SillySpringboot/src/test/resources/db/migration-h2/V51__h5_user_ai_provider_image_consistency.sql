ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS image_character_consistency_mode VARCHAR(16) NOT NULL DEFAULT 'balanced';

ALTER TABLE app_h5_user_ai_provider ADD COLUMN IF NOT EXISTS image_reference_source_mode VARCHAR(32) NOT NULL DEFAULT 'latest_generated_first';

