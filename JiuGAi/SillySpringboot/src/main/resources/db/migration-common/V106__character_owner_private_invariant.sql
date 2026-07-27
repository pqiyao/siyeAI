-- A user-owned character is always private; a system character never has an owner.
-- Repair contradictory legacy rows before enforcing the invariant.

UPDATE app_character
SET private_card = TRUE,
    client_visible = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE owner_user_id IS NOT NULL
  AND private_card = FALSE;

UPDATE app_character
SET client_visible = FALSE,
    deleted_at = COALESCE(deleted_at, CURRENT_TIMESTAMP),
    private_card = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE owner_user_id IS NULL
  AND private_card = TRUE;

ALTER TABLE app_character
    ADD CONSTRAINT ck_character_owner_private
    CHECK (
        (owner_user_id IS NULL AND private_card = FALSE)
        OR (owner_user_id IS NOT NULL AND private_card = TRUE)
    );
