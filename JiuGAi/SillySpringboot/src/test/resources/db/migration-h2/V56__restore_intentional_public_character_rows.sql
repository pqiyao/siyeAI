-- V54 hides public rows that share an ST avatar with private user cards.
-- Keep pure feed shadows hidden, but restore public rows that carry real admin/imported metadata.
UPDATE app_character
SET deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted_at IS NOT NULL
  AND private_card = 0
  AND owner_user_id IS NULL
  AND st_avatar_url IS NOT NULL
  AND st_avatar_url != ''
  AND (
        (avatar_url IS NOT NULL AND avatar_url != '')
     OR (cover_url IS NOT NULL AND cover_url != '')
     OR (creator_name IS NOT NULL AND creator_name != '')
     OR (creator_handle IS NOT NULL AND creator_handle != '')
     OR (tags_json IS NOT NULL AND tags_json != '' AND tags_json != '[]')
     OR (bio IS NOT NULL AND bio != '')
     OR (persona IS NOT NULL AND persona != '')
     OR (first_message IS NOT NULL AND first_message != '')
  )
  AND EXISTS (
      SELECT 1
      FROM app_character private_row
      WHERE private_row.st_avatar_url = app_character.st_avatar_url
        AND private_row.private_card = 1
        AND private_row.owner_user_id IS NOT NULL
  );
