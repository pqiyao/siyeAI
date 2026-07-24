-- V90: wallet consume idempotency + epay payment enrichment

-- app_wallet_ledger: biz_ref + idempotency_key
SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_wallet_ledger'
              AND column_name = 'biz_ref'
        ),
        'SELECT 1',
        'ALTER TABLE app_wallet_ledger ADD COLUMN biz_ref VARCHAR(64) NULL AFTER order_no'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_wallet_ledger'
              AND column_name = 'idempotency_key'
        ),
        'SELECT 1',
        'ALTER TABLE app_wallet_ledger ADD COLUMN idempotency_key VARCHAR(96) NULL AFTER biz_ref'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'app_wallet_ledger'
              AND index_name = 'uk_wallet_ledger_idempotency'
        ),
        'SELECT 1',
        'ALTER TABLE app_wallet_ledger ADD UNIQUE INDEX uk_wallet_ledger_idempotency (idempotency_key)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- app_payment_order: provider settlement fields
SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_payment_order'
              AND column_name = 'provider_trade_no'
        ),
        'SELECT 1',
        'ALTER TABLE app_payment_order ADD COLUMN provider_trade_no VARCHAR(128) NULL AFTER status'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_payment_order'
              AND column_name = 'paid_amount_cents'
        ),
        'SELECT 1',
        'ALTER TABLE app_payment_order ADD COLUMN paid_amount_cents INT NULL AFTER provider_trade_no'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_payment_order'
              AND column_name = 'notify_payload_hash'
        ),
        'SELECT 1',
        'ALTER TABLE app_payment_order ADD COLUMN notify_payload_hash VARCHAR(128) NULL AFTER paid_amount_cents'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- app_payment_channel_config: encrypted secrets
SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_payment_channel_config'
              AND column_name = 'config_cipher'
        ),
        'SELECT 1',
        'ALTER TABLE app_payment_channel_config ADD COLUMN config_cipher TEXT NULL AFTER note'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_payment_channel_config'
              AND column_name = 'config_updated_at'
        ),
        'SELECT 1',
        'ALTER TABLE app_payment_channel_config ADD COLUMN config_updated_at TIMESTAMP NULL AFTER config_cipher'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO app_payment_channel_config (
    channel_code, display_name, description, sort_order, enabled, client_visible, note
) VALUES (
    'epay',
    '易支付',
    '易支付/码支付聚合收银台（支付宝/微信等）。需配置商户 PID 与通信密钥。',
    40,
    0,
    0,
    '默认关闭；配置 PID/KEY 并开启后对客户端可见'
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    sort_order = VALUES(sort_order),
    note = VALUES(note);
