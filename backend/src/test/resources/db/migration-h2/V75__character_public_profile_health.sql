ALTER TABLE app_character ADD COLUMN IF NOT EXISTS public_summary VARCHAR(512) NULL;

ALTER TABLE app_character ADD COLUMN IF NOT EXISTS public_tags_json TEXT NULL;

ALTER TABLE app_character ADD COLUMN IF NOT EXISTS public_warnings_json TEXT NULL;

ALTER TABLE app_character ADD COLUMN IF NOT EXISTS health_score INT NOT NULL DEFAULT 0;

ALTER TABLE app_character ADD COLUMN IF NOT EXISTS health_issues_json TEXT NULL;

