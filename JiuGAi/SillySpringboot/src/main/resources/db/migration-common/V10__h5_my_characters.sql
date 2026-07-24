-- H5 compat: extend app_character for "mine/editor" fields (soft-deletable, user-owned)

ALTER TABLE app_character ADD COLUMN owner_user_id BIGINT NULL;
ALTER TABLE app_character ADD COLUMN tagline VARCHAR(255) NULL;
ALTER TABLE app_character ADD COLUMN bio TEXT NULL;
ALTER TABLE app_character ADD COLUMN persona TEXT NULL;
ALTER TABLE app_character ADD COLUMN scenario TEXT NULL;
ALTER TABLE app_character ADD COLUMN first_message TEXT NULL;
ALTER TABLE app_character ADD COLUMN alternate_greetings_json TEXT NULL;
ALTER TABLE app_character ADD COLUMN mes_example TEXT NULL;
ALTER TABLE app_character ADD COLUMN system_prompt TEXT NULL;
ALTER TABLE app_character ADD COLUMN post_history_instructions TEXT NULL;
ALTER TABLE app_character ADD COLUMN avatar_url VARCHAR(512) NULL;
ALTER TABLE app_character ADD COLUMN cover_url VARCHAR(512) NULL;
ALTER TABLE app_character ADD COLUMN private_card BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_character ADD COLUMN deleted_at TIMESTAMP NULL;

CREATE INDEX idx_character_owner_user_id ON app_character(owner_user_id);
CREATE INDEX idx_character_deleted_at ON app_character(deleted_at);

