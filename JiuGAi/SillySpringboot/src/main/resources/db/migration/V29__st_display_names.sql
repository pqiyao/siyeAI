ALTER TABLE app_h5_profile
    ADD COLUMN st_display_name VARCHAR(64) NULL AFTER display_name;

ALTER TABLE app_conversation_st_binding
    ADD COLUMN st_display_name_override VARCHAR(64) NULL AFTER st_world_names_json;
