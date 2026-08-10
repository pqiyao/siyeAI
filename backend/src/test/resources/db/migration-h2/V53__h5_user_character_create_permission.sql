ALTER TABLE app_h5_user_profile_ext ADD COLUMN IF NOT EXISTS character_create_allowed TINYINT(1) NOT NULL DEFAULT 0;

