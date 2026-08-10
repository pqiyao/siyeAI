SET @jg_drop_unique_character_avatar = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character'
              AND INDEX_NAME = 'uk_character_st_avatar_url'
        ),
        'DROP INDEX uk_character_st_avatar_url ON app_character',
        'SELECT 1'
    )
);
PREPARE stmt FROM @jg_drop_unique_character_avatar;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_character_avatar_index = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character'
              AND INDEX_NAME = 'idx_character_st_avatar_url'
        ),
        'SELECT 1',
        'CREATE INDEX idx_character_st_avatar_url ON app_character(st_avatar_url)'
    )
);
PREPARE stmt FROM @jg_add_character_avatar_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @jg_add_character_private_avatar_index = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_character'
              AND INDEX_NAME = 'idx_character_private_avatar_deleted'
        ),
        'SELECT 1',
        'CREATE INDEX idx_character_private_avatar_deleted ON app_character(private_card, st_avatar_url, deleted_at)'
    )
);
PREPARE stmt FROM @jg_add_character_private_avatar_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
