ALTER TABLE products
    ADD CONSTRAINT products_price_non_negative
        CHECK (price >= 0),
    ADD CONSTRAINT products_stock_non_negative
        CHECK (stock_quantity >= 0);

ALTER TABLE cart_items
    ADD CONSTRAINT cart_items_quantity_positive
        CHECK (quantity > 0);

ALTER TABLE order_items
    ADD CONSTRAINT order_items_quantity_positive
        CHECK (quantity > 0),
    ADD CONSTRAINT order_items_unit_price_non_negative
        CHECK (unit_price >= 0),
    ADD CONSTRAINT order_items_subtotal_non_negative
        CHECK (subtotal >= 0);

ALTER TABLE orders
    ADD CONSTRAINT orders_subtotal_non_negative
        CHECK (subtotal >= 0),
    ADD CONSTRAINT orders_shipping_fee_non_negative
        CHECK (shipping_fee >= 0),
    ADD CONSTRAINT orders_discount_amount_non_negative
        CHECK (discount_amount >= 0),
    ADD CONSTRAINT orders_total_amount_non_negative
        CHECK (total_amount >= 0);

ALTER TABLE sub_orders
    ADD CONSTRAINT sub_orders_subtotal_non_negative
        CHECK (subtotal >= 0),
    ADD CONSTRAINT sub_orders_shipping_fee_non_negative
        CHECK (shipping_fee >= 0),
    ADD CONSTRAINT sub_orders_discount_amount_non_negative
        CHECK (discount_amount >= 0),
    ADD CONSTRAINT sub_orders_total_amount_non_negative
        CHECK (total_amount >= 0);

ALTER TABLE payments
    ADD CONSTRAINT payments_amount_non_negative
        CHECK (amount >= 0);

ALTER TABLE reviews
    ADD CONSTRAINT reviews_rating_valid
        CHECK (rating >= 1 AND rating <= 5);

ALTER TABLE coupons
    ADD CONSTRAINT coupons_discount_percent_valid
        CHECK (discount_percent >= 0 AND discount_percent <= 100);

ALTER TABLE vendor_finance_configs
    ADD CONSTRAINT vendor_finance_configs_commission_percent_valid
        CHECK (commission_percent >= 0 AND commission_percent <= 100);

ALTER TABLE vendor_payouts
    ADD CONSTRAINT vendor_payouts_amount_non_negative
        CHECK (amount >= 0);

ALTER TABLE delivery_zones
    ADD CONSTRAINT delivery_zones_shipping_fee_non_negative
        CHECK (shipping_fee >= 0);
