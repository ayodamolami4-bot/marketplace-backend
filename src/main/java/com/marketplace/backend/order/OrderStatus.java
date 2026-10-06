package com.marketplace.backend.order;

public enum OrderStatus {
    PENDING_PAYMENT,
    PENDING_FULFILLMENT,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}