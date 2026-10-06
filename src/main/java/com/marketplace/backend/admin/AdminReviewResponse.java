package com.marketplace.backend.admin;

import com.marketplace.backend.review.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminReviewResponse(
        UUID id,
        UUID userId,
        String userName,
        UUID productId,
        String productName,
        int rating,
        String comment,
        ReviewStatus status,
        String rejectionReason,
        Instant createdAt
) {
}