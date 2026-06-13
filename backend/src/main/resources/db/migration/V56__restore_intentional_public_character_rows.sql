-- V54 hides public rows that share an ST avatar with private user cards.
-- Keep pure feed shadows hidden, but restore public rows that carry real admin/imported metadata.
UPDATE app_character public_row
JOIN app_character private_row
  ON private_row.st_avatar_url = public_row.st_avatar_url
 AND private_row.private_card = 1
 AND private_row.owner_user_id IS NOT NULL
SET public_row.deleted_at = NULL,
    public_row.updated_at = CURRENT_TIMESTAMP
WHERE public_row.deleted_at IS NOT NULL
  AND public_row.private_card = 0
  AND public_row.owner_user_id IS NULL
  AND public_row.st_avatar_url IS NOT NULL
  AND public_row.st_avatar_url != ''
  AND (
        (public_row.avatar_url IS NOT NULL AND public_row.avatar_url != '')
     OR (public_row.cover_url IS NOT NULL AND public_row.cover_url != '')
     OR (public_row.creator_name IS NOT NULL AND public_row.creator_name != '')
     OR (public_row.creator_handle IS NOT NULL AND public_row.creator_handle != '')
     OR (public_row.tags_json IS NOT NULL AND public_row.tags_json != '' AND public_row.tags_json != '[]')
     OR (public_row.bio IS NOT NULL AND public_row.bio != '')
     OR (public_row.persona IS NOT NULL AND public_row.persona != '')
     OR (public_row.first_message IS NOT NULL AND public_row.first_message != '')
  );
