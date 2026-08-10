UPDATE app_character
SET deleted_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted_at IS NULL
  AND private_card = 0
  AND owner_user_id IS NULL
  AND (avatar_url IS NULL OR avatar_url = '')
  AND (cover_url IS NULL OR cover_url = '')
  AND (creator_name IS NULL OR creator_name = '')
  AND (creator_handle IS NULL OR creator_handle = '')
  AND (tags_json IS NULL OR tags_json = '' OR tags_json = '[]')
  AND EXISTS (
      SELECT 1
      FROM app_character private_row
      WHERE private_row.st_avatar_url = app_character.st_avatar_url
        AND private_row.private_card = 1
        AND private_row.deleted_at IS NULL
        AND app_character.id <> private_row.id
  );
