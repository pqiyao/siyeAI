-- V94: prevent one provider trade number from settling multiple orders.
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_order_provider_trade_no
    ON app_payment_order(provider_trade_no);
