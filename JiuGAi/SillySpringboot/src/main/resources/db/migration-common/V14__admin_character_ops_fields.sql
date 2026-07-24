-- 管理端 / H5 运营字段：标签、玩法、VIP、排序、创作者等

ALTER TABLE app_character ADD COLUMN occupation_label VARCHAR(255) NULL;
ALTER TABLE app_character ADD COLUMN tags_json TEXT NULL;
ALTER TABLE app_character ADD COLUMN vip_only BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_character ADD COLUMN unlocked_default BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_character ADD COLUMN like_count INT NOT NULL DEFAULT 0;
ALTER TABLE app_character ADD COLUMN dislike_count INT NOT NULL DEFAULT 0;
ALTER TABLE app_character ADD COLUMN creator_name VARCHAR(255) NULL;
ALTER TABLE app_character ADD COLUMN creator_handle VARCHAR(255) NULL;
ALTER TABLE app_character ADD COLUMN token_display VARCHAR(64) NULL;
ALTER TABLE app_character ADD COLUMN gameplay_type VARCHAR(128) NULL;
ALTER TABLE app_character ADD COLUMN chat_modes_json TEXT NULL;
ALTER TABLE app_character ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE app_character ADD COLUMN creator_notes TEXT NULL;
ALTER TABLE app_character ADD COLUMN st_extra_json TEXT NULL;

CREATE INDEX idx_character_sort ON app_character(sort_order, id);
CREATE INDEX idx_character_gameplay ON app_character(gameplay_type);
