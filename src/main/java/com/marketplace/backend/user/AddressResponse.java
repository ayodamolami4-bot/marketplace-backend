package com.marketplace.backend.user;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String recipientName,
        String phoneNumber,
        String addressLine,
        String city,
        String state,
        String country,
        String postalCode,
        boolean defaultAddress,
        Instant createdAt,
        Instant updatedAt
) {
}