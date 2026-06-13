-- H5 private PNG imports are saved in ST with h5_u{userId}_{uuid}.png names.
-- These files must never be treated as public/system discover cards.
UPDATE app_character
SET deleted_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted_at IS NULL
  AND private_card = 0
  AND owner_user_id IS NULL
  AND st_avatar_url REGEXP '^h5_u[0-9]+_[0-9A-Fa-f]{32}\\.png$';
