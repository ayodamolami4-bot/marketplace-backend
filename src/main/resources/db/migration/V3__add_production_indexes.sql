CREATE INDEX IF NOT EXISTS idx_user_roles_user_id
    ON user_roles (user_id);

CREATE INDEX IF NOT EXISTS idx_cart_items_user_created_at
    ON cart_items (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_orders_user_created_at
    ON orders (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sub_orders_vendor_created_at
    ON sub_orders (vendor_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read_created_at
    ON notifications (user_id, read, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payments_method_status_created_at
    ON payments (method, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_products_vendor_created_at
    ON products (vendor_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reviews_product_status_created_at
    ON reviews (product_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_delivery_events_sub_order_created_at
    ON delivery_tracking_events (sub_order_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_vendor_payouts_vendor_created_at
    ON vendor_payouts (vendor_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_vendors_status_created_at
    ON vendors (status, created_at ASC);
