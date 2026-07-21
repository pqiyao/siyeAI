CREATE TABLE IF NOT EXISTS app_stats_counter (
    counter_key VARCHAR(64) NOT NULL PRIMARY KEY,
    counter_value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO app_stats_counter (counter_key, counter_value)
SELECT 'total_messages', COUNT(*)
FROM app_message
ON DUPLICATE KEY UPDATE
    counter_value = GREATEST(counter_value, VALUES(counter_value)),
    updated_at = CURRENT_TIMESTAMP;
