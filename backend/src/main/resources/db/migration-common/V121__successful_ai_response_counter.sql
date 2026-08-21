INSERT INTO app_stats_counter (counter_key, counter_value)
SELECT 'successful_ai_responses', COUNT(*)
FROM app_generation_stat_event
WHERE status = 'SUCCESS'
  AND channel IN ('CHAT_SYNC', 'CHAT_STREAM', 'CONTINUE', 'CONTINUE_STREAM', 'REGEN', 'REGEN_STREAM')
ON DUPLICATE KEY UPDATE
    counter_value = GREATEST(counter_value, VALUES(counter_value)),
    updated_at = CURRENT_TIMESTAMP;
