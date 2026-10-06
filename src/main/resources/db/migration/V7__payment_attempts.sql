CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    provider VARCHAR(50) NOT NULL,
    reference VARCHAR(255) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    authorization_url TEXT,
    access_code VARCHAR(255),
    initiated_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_payment_attempts_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(id),

    CONSTRAINT uk_payment_attempts_payment_number
        UNIQUE (payment_id, attempt_number),

    CONSTRAINT uk_payment_attempts_reference
        UNIQUE (reference),

    CONSTRAINT chk_payment_attempts_attempt_number
        CHECK (attempt_number > 0),

    CONSTRAINT chk_payment_attempts_amount
        CHECK (amount >= 0),

    CONSTRAINT chk_payment_attempts_status
        CHECK (
            status IN (
                'INITIATED',
                'PENDING',
                'SUCCESS',
                'FAILED',
                'EXPIRED'
            )
        )
);

CREATE INDEX idx_payment_attempts_payment
    ON payment_attempts (payment_id);

CREATE INDEX idx_payment_attempts_status
    ON payment_attempts (status);

CREATE INDEX idx_payment_attempts_reference
    ON payment_attempts (reference);

CREATE INDEX idx_payment_attempts_recovery
    ON payment_attempts (status, initiated_at ASC)
    WHERE status IN ('INITIATED', 'PENDING');
