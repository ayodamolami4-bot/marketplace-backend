package com.marketplace.backend.coupon;

import java.time.Instant;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        int discountPercent,
        Instant expiresAt
) {
}