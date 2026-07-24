-- V54 was intentionally conservative, but it also hid public/ST rows that were uploaded
-- after a user card with the same ST avatar had already been deleted. Restore those
-- later public rows; old pure sync shadows from before the delete stay hidden.
UPDATE app_character
SET deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted_at IS NOT NULL
  AND private_card = 0
  AND owner_user_id IS NULL
  AND st_avatar_url IS NOT NULL
  AND st_avatar_url != ''
  AND created_at IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM app_character private_row
      WHERE private_row.st_avatar_url = app_character.st_avatar_url
        AND private_row.private_card = 1
        AND private_row.owner_user_id IS NOT NULL
        AND private_row.deleted_at IS NOT NULL
        AND app_character.created_at > private_row.deleted_at
  );
