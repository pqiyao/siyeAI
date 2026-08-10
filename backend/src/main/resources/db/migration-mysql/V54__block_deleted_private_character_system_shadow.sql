UPDATE app_character public_row
JOIN app_character private_row
  ON private_row.st_avatar_url = public_row.st_avatar_url
 AND private_row.private_card = 1
 AND private_row.owner_user_id IS NOT NULL
SET public_row.deleted_at = CURRENT_TIMESTAMP,
    public_row.updated_at = CURRENT_TIMESTAMP
WHERE public_row.deleted_at IS NULL
  AND public_row.private_card = 0
  AND public_row.owner_user_id IS NULL
  AND public_row.st_avatar_url IS NOT NULL
  AND public_row.st_avatar_url != '';
