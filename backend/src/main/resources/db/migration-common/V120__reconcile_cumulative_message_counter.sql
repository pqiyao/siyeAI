INSERT INTO app_stats_counter (counter_key, counter_value)
SELECT 'total_messages', COUNT(*)
FROM app_message
ON DUPLICATE KEY UPDATE
    counter_value = GREATEST(counter_value, VALUES(counter_value)),
    updated_at = CURRENT_TIMESTAMP;
