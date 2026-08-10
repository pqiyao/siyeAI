-- Signed ST proxy URLs expire and must never be persisted as character media.
-- Normalize legacy proxy references to each row's own ST character file.
UPDATE app_character
SET avatar_url = CASE
        WHEN avatar_url LIKE '/api/v1/st-assets/characters/%'
          OR avatar_url LIKE '/api/v1/st-assets/characters-thumb/%'
            THEN st_avatar_url
        ELSE avatar_url
    END,
    cover_url = CASE
        WHEN cover_url LIKE '/api/v1/st-assets/characters/%'
          OR cover_url LIKE '/api/v1/st-assets/characters-thumb/%'
            THEN st_avatar_url
        ELSE cover_url
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE st_avatar_url IS NOT NULL
  AND st_avatar_url <> ''
  AND (
      avatar_url LIKE '/api/v1/st-assets/characters/%'
      OR avatar_url LIKE '/api/v1/st-assets/characters-thumb/%'
      OR cover_url LIKE '/api/v1/st-assets/characters/%'
      OR cover_url LIKE '/api/v1/st-assets/characters-thumb/%'
  );
