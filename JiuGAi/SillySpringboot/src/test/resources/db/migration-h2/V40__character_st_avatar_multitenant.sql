DROP INDEX IF EXISTS uk_character_st_avatar_url;

CREATE INDEX IF NOT EXISTS idx_character_st_avatar_url ON app_character(st_avatar_url);

CREATE INDEX IF NOT EXISTS idx_character_private_avatar_deleted ON app_character(private_card, st_avatar_url, deleted_at);

