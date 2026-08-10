-- V94: prevent one provider trade number from settling multiple orders.
SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'app_payment_order'
              AND index_name = 'uk_payment_order_provider_trade_no'
        ),
        'SELECT 1',
        'ALTER TABLE app_payment_order ADD UNIQUE INDEX uk_payment_order_provider_trade_no (provider_trade_no)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
