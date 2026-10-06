package com.marketplace.backend.review;

import java.time.Instant;
import java.util.UUID;

public record ProductReviewResponse(
        UUID id,
        UUID userId,
        String userName,
        int rating,
        String comment,
        Instant createdAt
) {
}