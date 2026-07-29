-- V111: let users remove unpaid orders from their wallet without breaking payment callbacks.
ALTER TABLE app_payment_order ADD COLUMN IF NOT EXISTS user_hidden BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_payment_order_user_visible_id
    ON app_payment_order(user_id, user_hidden, id);
