ALTER TABLE app_tag_library
    MODIFY COLUMN discover_visible TINYINT NOT NULL DEFAULT 1,
    MODIFY COLUMN discover_recommended TINYINT NOT NULL DEFAULT 0,
    MODIFY COLUMN detail_visible TINYINT NOT NULL DEFAULT 1;

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
