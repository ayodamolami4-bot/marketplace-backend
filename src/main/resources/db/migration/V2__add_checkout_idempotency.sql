CREATE TABLE checkout_idempotency (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,

    order_id UUID,

    status VARCHAR(20) NOT NULL,

    response_status INTEGER,
    response_body TEXT,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_checkout_idempotency_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_checkout_idempotency_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT uk_checkout_idempotency_user_key
        UNIQUE (user_id, idempotency_key),

    CONSTRAINT uk_checkout_idempotency_order
        UNIQUE (order_id)
);

CREATE INDEX idx_checkout_idempotency_status
    ON checkout_idempotency(status);

CREATE INDEX idx_checkout_idempotency_created_at
    ON checkout_idempotency(created_at);