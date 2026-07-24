UPDATE app_payment_channel_config
SET enabled = 0,
    client_visible = 0,
    note = '安全默认：模拟支付仅可在非生产环境由管理员显式开启'
WHERE channel_code IN ('mock_wechat', 'mock_alipay');
