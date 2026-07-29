ALTER TABLE app_payment_order
    ADD COLUMN expires_at TIMESTAMP;

UPDATE app_payment_order
SET expires_at = DATEADD('MINUTE', 2, created_at)
WHERE expires_at IS NULL;

UPDATE app_payment_order
SET status = 'CLOSED',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'PENDING'
  AND expires_at <= CURRENT_TIMESTAMP;

ALTER TABLE app_payment_order
    ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX idx_payment_order_pending_expiry
    ON app_payment_order(status, expires_at);
