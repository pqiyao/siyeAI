SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_h5_user_profile_ext'
              AND column_name = 'vip_expires_at'
        ),
        'SELECT 1',
        'ALTER TABLE app_h5_user_profile_ext ADD COLUMN vip_expires_at TIMESTAMP NULL AFTER vip_type'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_h5_user_profile_ext'
              AND column_name = 'daily_chat_quota'
        ),
        'SELECT 1',
        'ALTER TABLE app_h5_user_profile_ext ADD COLUMN daily_chat_quota INT NOT NULL DEFAULT 0 AFTER gold_coin'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_h5_user_profile_ext'
              AND column_name = 'daily_image_quota'
        ),
        'SELECT 1',
        'ALTER TABLE app_h5_user_profile_ext ADD COLUMN daily_image_quota INT NOT NULL DEFAULT 0 AFTER daily_chat_quota'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS app_store_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    subtitle VARCHAR(255) NULL,
    product_type VARCHAR(32) NOT NULL,
    price_cents INT NOT NULL DEFAULT 0,
    score_amount INT NOT NULL DEFAULT 0,
    gold_coin_amount INT NOT NULL DEFAULT 0,
    vip_type INT NOT NULL DEFAULT 0,
    vip_days INT NOT NULL DEFAULT 0,
    tag_label VARCHAR(64) NULL,
    badge_label VARCHAR(64) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_store_product_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS app_payment_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    amount_cents INT NOT NULL DEFAULT 0,
    score_amount INT NOT NULL DEFAULT 0,
    gold_coin_amount INT NOT NULL DEFAULT 0,
    vip_type INT NOT NULL DEFAULT 0,
    vip_days INT NOT NULL DEFAULT 0,
    payment_channel VARCHAR(32) NOT NULL DEFAULT 'mock_wechat',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_order_no UNIQUE (order_no),
    CONSTRAINT fk_payment_order_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_payment_order_product FOREIGN KEY (product_id) REFERENCES app_store_product(id)
);

CREATE TABLE IF NOT EXISTS app_wallet_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    order_no VARCHAR(64) NULL,
    delta_score INT NOT NULL DEFAULT 0,
    delta_gold_coin INT NOT NULL DEFAULT 0,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_ledger_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

INSERT INTO app_store_product (
    code, name, subtitle, product_type, price_cents, score_amount, gold_coin_amount,
    vip_type, vip_days, tag_label, badge_label, enabled, sort_order
)
SELECT
    'coin_small', '小额钻石包', '适合日常聊天补给', 'COIN', 990, 120, 1200,
    0, 0, '推荐', '首充友好', 1, 10
WHERE NOT EXISTS (
    SELECT 1 FROM app_store_product WHERE code = 'coin_small'
);

INSERT INTO app_store_product (
    code, name, subtitle, product_type, price_cents, score_amount, gold_coin_amount,
    vip_type, vip_days, tag_label, badge_label, enabled, sort_order
)
SELECT
    'coin_plus', '进阶钻石包', '适合重度聊天与角色收藏', 'COIN', 2990, 420, 4200,
    0, 0, '热卖', '赠送金币', 1, 20
WHERE NOT EXISTS (
    SELECT 1 FROM app_store_product WHERE code = 'coin_plus'
);

INSERT INTO app_store_product (
    code, name, subtitle, product_type, price_cents, score_amount, gold_coin_amount,
    vip_type, vip_days, tag_label, badge_label, enabled, sort_order
)
SELECT
    'vip_week', '周卡会员', '7 天体验高级权益与更高额度', 'VIP', 2800, 280, 2800,
    1, 7, '体验款', '建议先试试', 1, 30
WHERE NOT EXISTS (
    SELECT 1 FROM app_store_product WHERE code = 'vip_week'
);

INSERT INTO app_store_product (
    code, name, subtitle, product_type, price_cents, score_amount, gold_coin_amount,
    vip_type, vip_days, tag_label, badge_label, enabled, sort_order
)
SELECT
    'vip_month', '月卡 Plus', '30 天稳定权益，适合长期沉浸体验', 'VIP', 9800, 1280, 12800,
    2, 30, '长期推荐', '额度更高', 1, 40
WHERE NOT EXISTS (
    SELECT 1 FROM app_store_product WHERE code = 'vip_month'
);
