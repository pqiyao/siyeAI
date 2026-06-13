UPDATE app_h5_user_profile_ext
SET daily_chat_quota = CASE
    WHEN chat_quota_override IS NOT NULL THEN daily_chat_quota
    WHEN COALESCE(vip_type, 0) >= 2
         AND vip_expires_at IS NOT NULL
         AND vip_expires_at > NOW()
        THEN 1000
    WHEN COALESCE(vip_type, 0) >= 1
         AND vip_expires_at IS NOT NULL
         AND vip_expires_at > NOW()
        THEN 300
    ELSE 100
END
WHERE chat_quota_override IS NULL
  AND daily_chat_quota IN (20, 80, 200);
