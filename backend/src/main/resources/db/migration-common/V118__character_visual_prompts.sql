ALTER TABLE app_character
    ADD COLUMN visual_prompt TEXT NULL;

ALTER TABLE app_character
    ADD COLUMN visual_negative_prompt TEXT NULL;

ALTER TABLE app_character_member
    ADD COLUMN visual_prompt TEXT NULL;

ALTER TABLE app_character_member
    ADD COLUMN visual_negative_prompt TEXT NULL;
