package com.marketplace.backend.wishlist;

import java.time.Instant;
import java.util.UUID;

public record WishlistItemResponse(
        UUID id,
        UUID productId,
        String productName,
        long price,
        String vendorName,
        Instant createdAt
) {
}