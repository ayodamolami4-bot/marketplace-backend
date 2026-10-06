package com.marketplace.backend.checkout;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "checkout_idempotency",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_checkout_idempotency_user_key",
                        columnNames = {
                                "user_id",
                                "idempotency_key"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_checkout_idempotency_order",
                        columnNames = "order_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_checkout_idempotency_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_checkout_idempotency_created_at",
                        columnList = "created_at"
                )
        }
)
public class CheckoutIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
            name = "user_id",
            nullable = false
    )
    private UUID userId;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 255
    )
    private String idempotencyKey;

    @Column(
            name = "request_hash",
            nullable = false,
            length = 64
    )
    private String requestHash;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private CheckoutIdempotencyStatus status =
            CheckoutIdempotencyStatus.PROCESSING;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(
            name = "response_body",
            columnDefinition = "TEXT"
    )
    private String responseBody;

    @Column(
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public CheckoutIdempotency() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public CheckoutIdempotencyStatus getStatus() {
        return status;
    }

    public void setStatus(
            CheckoutIdempotencyStatus status
    ) {
        this.status = status;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}