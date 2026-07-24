ALTER TABLE app_character
    ADD COLUMN client_visible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN preview_blur_vip_level TINYINT NOT NULL DEFAULT 0;

CREATE INDEX idx_character_client_visible ON app_character(client_visible, sort_order, id);
