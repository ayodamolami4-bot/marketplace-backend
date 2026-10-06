package com.marketplace.backend.product;

import java.util.UUID;

public record ProductListResponse(
        UUID id,
        String name,
        long price,
        String thumbnail,
        VendorSummary vendor,
        Double avgRating,
        String category
) {

    public record VendorSummary(
            UUID id,
            String name
    ) {
    }
}
