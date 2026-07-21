ALTER TABLE app_h5_visitor_device
    ADD COLUMN anonymous_chat_attempt_count INT NOT NULL DEFAULT 0 AFTER user_agent,
    ADD COLUMN anonymous_conversation_create_count INT NOT NULL DEFAULT 0 AFTER anonymous_chat_attempt_count,
    ADD COLUMN anonymous_character_create_count INT NOT NULL DEFAULT 0 AFTER anonymous_conversation_create_count;
