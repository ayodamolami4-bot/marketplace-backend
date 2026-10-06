package com.marketplace.backend.payment;

public record PaymentRetryResponse(
        String authorizationUrl,
        String reference
) {
}