ALTER TABLE app_lorebook_entry
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'manual' AFTER enabled;

ALTER TABLE app_lorebook_entry
    ADD COLUMN raw_entry_json LONGTEXT NULL AFTER source_type;
