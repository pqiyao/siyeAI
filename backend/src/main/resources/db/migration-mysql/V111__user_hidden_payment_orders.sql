-- V111: let users remove unpaid orders from their wallet without breaking payment callbacks.
SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'app_payment_order'
              AND column_name = 'user_hidden'
        ),
        'SELECT 1',
        'ALTER TABLE app_payment_order ADD COLUMN user_hidden TINYINT(1) NOT NULL DEFAULT 0 AFTER status'
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
              AND table_name = 'app_payment_order'
              AND index_name = 'idx_payment_order_user_visible_id'
        ),
        'SELECT 1',
        'CREATE INDEX idx_payment_order_user_visible_id ON app_payment_order(user_id, user_hidden, id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
