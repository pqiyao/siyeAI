ALTER TABLE app_payment_order
    ADD COLUMN expires_at DATETIME(6) NULL AFTER status;

UPDATE app_payment_order
SET expires_at = DATE_ADD(created_at, INTERVAL 2 MINUTE)
WHERE expires_at IS NULL;

UPDATE app_payment_order
SET status = 'CLOSED',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'PENDING'
  AND expires_at <= CURRENT_TIMESTAMP;

ALTER TABLE app_payment_order
    MODIFY COLUMN expires_at DATETIME(6) NOT NULL;

CREATE INDEX idx_payment_order_pending_expiry
    ON app_payment_order(status, expires_at);
