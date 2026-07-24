SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_tag_library'
              AND COLUMN_NAME = 'discover_visible'
        ),
        'SELECT 1',
        'ALTER TABLE app_tag_library ADD COLUMN discover_visible TINYINT NOT NULL DEFAULT 1 AFTER enabled'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_tag_library'
              AND COLUMN_NAME = 'discover_recommended'
        ),
        'SELECT 1',
        'ALTER TABLE app_tag_library ADD COLUMN discover_recommended TINYINT NOT NULL DEFAULT 0 AFTER discover_visible'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'app_tag_library'
              AND COLUMN_NAME = 'detail_visible'
        ),
        'SELECT 1',
        'ALTER TABLE app_tag_library ADD COLUMN detail_visible TINYINT NOT NULL DEFAULT 1 AFTER discover_recommended'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE app_tag_library
SET discover_visible = enabled,
    detail_visible = enabled,
    discover_recommended = CASE
        WHEN LOWER(TRIM(code)) IN ('奇幻', '校园', '恋爱', '冒险', '日常', '悬疑', '科幻', '古代', '现代', '都市', '原创', '同人', '治愈', '纯爱', '养成', '群聊', '群像', '状态栏', '记忆体')
            OR LOWER(TRIM(name)) IN ('奇幻', '校园', '恋爱', '冒险', '日常', '悬疑', '科幻', '古代', '现代', '都市', '原创', '同人', '治愈', '纯爱', '养成', '群聊', '群像', '状态栏', '记忆体')
            THEN 1
        ELSE discover_recommended
    END
WHERE discover_visible <> enabled
   OR detail_visible <> enabled
   OR LOWER(TRIM(code)) IN ('奇幻', '校园', '恋爱', '冒险', '日常', '悬疑', '科幻', '古代', '现代', '都市', '原创', '同人', '治愈', '纯爱', '养成', '群聊', '群像', '状态栏', '记忆体')
   OR LOWER(TRIM(name)) IN ('奇幻', '校园', '恋爱', '冒险', '日常', '悬疑', '科幻', '古代', '现代', '都市', '原创', '同人', '治愈', '纯爱', '养成', '群聊', '群像', '状态栏', '记忆体');
