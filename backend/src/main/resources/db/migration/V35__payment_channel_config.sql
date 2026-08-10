CREATE TABLE IF NOT EXISTS app_payment_channel_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 100,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    client_visible TINYINT(1) NOT NULL DEFAULT 1,
    note VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_channel_code UNIQUE (channel_code)
);

INSERT INTO app_payment_channel_config (
    channel_code, display_name, description, sort_order, enabled, client_visible, note
) VALUES
    ('wechat_h5', '微信 H5', '微信公众号/H5 页面拉起微信支付。需要商户号、API v3 密钥与商户证书。', 10, 0, 0, '生产渠道，建议在商户配置齐全后启用'),
    ('alipay_wap', '支付宝', '手机网站支付，适合 H5 页面直接跳转支付宝收银台。', 20, 0, 0, '生产渠道，建议在应用审核通过后启用'),
    ('telegram_star', 'Telegram Stars', 'Telegram WebApp/机器人场景数字商品支付。', 30, 0, 0, '适用于 Telegram 端'),
    ('mock_wechat', '模拟微信支付', '开发/测试渠道，确认后直接发放权益。', 90, 1, 1, '默认仅测试环境可用'),
    ('mock_alipay', '模拟支付宝', '开发/测试渠道，确认后直接发放权益。', 91, 1, 1, '默认仅测试环境可用'),
    ('card_code', '卡密兑换', '预留的卡密/兑换码支付通道。', 120, 0, 0, '暂未开放')
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    sort_order = VALUES(sort_order),
    note = VALUES(note);
