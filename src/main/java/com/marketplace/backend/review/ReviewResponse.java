package com.marketplace.backend.review;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID productId,
        int rating,
        String comment,
        ReviewStatus status,
        Instant createdAt
) {
}