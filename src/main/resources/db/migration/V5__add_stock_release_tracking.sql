ALTER TABLE orders
    ADD COLUMN stock_released_at TIMESTAMPTZ;

CREATE INDEX idx_orders_pending_payment_recovery
    ON orders (created_at ASC)
    WHERE status = 'PENDING_PAYMENT'
      AND stock_released_at IS NULL;
