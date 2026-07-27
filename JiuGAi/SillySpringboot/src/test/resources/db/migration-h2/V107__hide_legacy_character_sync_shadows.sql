-- H2 equivalent of the MySQL legacy-shadow cleanup.

UPDATE app_character system_row
SET client_visible = FALSE,
    deleted_at = COALESCE(system_row.deleted_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
WHERE system_row.owner_user_id IS NULL
  AND system_row.private_card = FALSE
  AND system_row.deleted_at IS NULL
  AND (
      EXISTS (
          SELECT 1
          FROM app_character private_row
          WHERE private_row.st_avatar_url = system_row.st_avatar_url
            AND private_row.owner_user_id IS NOT NULL
            AND private_row.private_card = TRUE
            AND private_row.deleted_at IS NULL
      )
      OR LOWER(system_row.st_avatar_url) LIKE 'h5$_u%' ESCAPE '$'
      OR LOWER(system_row.st_avatar_url) LIKE 'h5draft$_u%' ESCAPE '$'
      OR (
          LOWER(system_row.st_avatar_url) LIKE 'system$_copy$_%' ESCAPE '$'
          AND NOT EXISTS (
              SELECT 1
              FROM app_character_system_promotion promotion
              WHERE promotion.target_character_id = system_row.id
          )
      )
  );
