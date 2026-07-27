-- Hide legacy system rows that could only have been produced by old automatic ST syncing.
-- Valid system_copy rows are retained when they have an explicit promotion audit record.

UPDATE app_character system_row
LEFT JOIN app_character private_row
    ON private_row.st_avatar_url = system_row.st_avatar_url
   AND private_row.owner_user_id IS NOT NULL
   AND private_row.private_card = TRUE
   AND private_row.deleted_at IS NULL
LEFT JOIN app_character_system_promotion promotion
    ON promotion.target_character_id = system_row.id
SET system_row.client_visible = FALSE,
    system_row.deleted_at = COALESCE(system_row.deleted_at, CURRENT_TIMESTAMP),
    system_row.updated_at = CURRENT_TIMESTAMP
WHERE system_row.owner_user_id IS NULL
  AND system_row.private_card = FALSE
  AND system_row.deleted_at IS NULL
  AND (
      private_row.id IS NOT NULL
      OR LOWER(system_row.st_avatar_url) LIKE 'h5$_u%' ESCAPE '$'
      OR LOWER(system_row.st_avatar_url) LIKE 'h5draft$_u%' ESCAPE '$'
      OR (
          LOWER(system_row.st_avatar_url) LIKE 'system$_copy$_%' ESCAPE '$'
          AND promotion.id IS NULL
      )
  );
