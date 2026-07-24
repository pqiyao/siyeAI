UPDATE app_character
SET deleted_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted_at IS NULL
  AND private_card = 0
  AND owner_user_id IS NULL
  AND st_avatar_url IS NOT NULL
  AND st_avatar_url != ''
  AND EXISTS (
      SELECT 1
      FROM app_character private_row
      WHERE private_row.st_avatar_url = app_character.st_avatar_url
        AND private_row.private_card = 1
        AND private_row.owner_user_id IS NOT NULL
  );
