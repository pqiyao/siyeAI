-- V92 (H2): wallet consume idempotency + epay payment enrichment

ALTER TABLE app_wallet_ledger ADD COLUMN IF NOT EXISTS biz_ref VARCHAR(64) NULL;
ALTER TABLE app_wallet_ledger ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(96) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_wallet_ledger_idempotency ON app_wallet_ledger(idempotency_key);

ALTER TABLE app_payment_order ADD COLUMN IF NOT EXISTS provider_trade_no VARCHAR(128) NULL;
ALTER TABLE app_payment_order ADD COLUMN IF NOT EXISTS paid_amount_cents INT NULL;
ALTER TABLE app_payment_order ADD COLUMN IF NOT EXISTS notify_payload_hash VARCHAR(128) NULL;

ALTER TABLE app_payment_channel_config ADD COLUMN IF NOT EXISTS config_cipher CLOB NULL;
ALTER TABLE app_payment_channel_config ADD COLUMN IF NOT EXISTS config_updated_at TIMESTAMP NULL;

MERGE INTO app_payment_channel_config (
    channel_code, display_name, description, sort_order, enabled, client_visible, note
) KEY (channel_code)
VALUES (
    'epay',
    'EPay',
    'EPay aggregate checkout. Configure merchant PID and key before enabling.',
    40,
    0,
    0,
    'Disabled by default'
);
