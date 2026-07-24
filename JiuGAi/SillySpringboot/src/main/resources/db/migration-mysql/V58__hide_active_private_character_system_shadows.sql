-- Active user-created cards must not appear as system cards.
-- Hide only lightweight ST sync shadows; real admin/imported system cards with metadata stay visible.
UPDATE app_character public_row
JOIN app_character private_row
  ON private_row.st_avatar_url = public_row.st_avatar_url
 AND private_row.private_card = 1
 AND private_row.owner_user_id IS NOT NULL
 AND private_row.deleted_at IS NULL
SET public_row.deleted_at = CURRENT_TIMESTAMP,
    public_row.updated_at = CURRENT_TIMESTAMP
WHERE public_row.deleted_at IS NULL
  AND public_row.private_card = 0
  AND public_row.owner_user_id IS NULL
  AND public_row.st_avatar_url IS NOT NULL
  AND public_row.st_avatar_url != ''
  AND (public_row.avatar_url IS NULL OR public_row.avatar_url = '')
  AND (public_row.cover_url IS NULL OR public_row.cover_url = '')
  AND (public_row.creator_name IS NULL OR public_row.creator_name = '')
  AND (public_row.creator_handle IS NULL OR public_row.creator_handle = '')
  AND (public_row.tags_json IS NULL OR public_row.tags_json = '' OR public_row.tags_json = '[]');
