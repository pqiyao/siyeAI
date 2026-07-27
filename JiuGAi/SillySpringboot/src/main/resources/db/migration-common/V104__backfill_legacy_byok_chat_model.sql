INSERT INTO app_h5_user_ai_chat_model (
    user_id,
    model_name,
    display_name,
    sort_order,
    default_model,
    enabled,
    last_test_status,
    created_at,
    updated_at
)
SELECT
    provider.user_id,
    provider.model_name,
    '',
    100,
    1,
    1,
    'unknown',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM app_h5_user_ai_provider provider
WHERE LOWER(TRIM(provider.provider_mode)) = 'custom'
  AND TRIM(provider.model_name) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM app_h5_user_ai_chat_model model
      WHERE model.user_id = provider.user_id
  );
