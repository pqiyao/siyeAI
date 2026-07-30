UPDATE app_ai_chat_offering_price
SET billing_mode = 'QUOTA_THEN_DIAMOND_OR_GOLD',
    updated_at = CURRENT_TIMESTAMP
WHERE billing_mode = 'QUOTA_THEN_MIXED';

UPDATE app_runtime_setting
SET setting_value = JSON_SET(setting_value, '$.chatWalletMode', 'DIAMOND_OR_GOLD'),
    updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'entitlement_policy'
  AND JSON_VALID(setting_value);
