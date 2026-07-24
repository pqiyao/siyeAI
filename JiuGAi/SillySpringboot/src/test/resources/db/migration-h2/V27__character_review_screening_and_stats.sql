ALTER TABLE app_character_review_log ADD COLUMN IF NOT EXISTS event_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL_REVIEW';

ALTER TABLE app_character_review_log ADD COLUMN IF NOT EXISTS screening_level VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE app_character_review_log ADD COLUMN IF NOT EXISTS screening_flags VARCHAR(500) NULL;

ALTER TABLE app_character_review_log ADD COLUMN IF NOT EXISTS screening_hits INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_character_review_log_status_created ON app_character_review_log (review_status, created_at);

CREATE INDEX IF NOT EXISTS idx_character_review_log_event_created ON app_character_review_log (event_type, created_at);

CREATE INDEX IF NOT EXISTS idx_character_review_log_screening_created ON app_character_review_log (screening_level, created_at);

