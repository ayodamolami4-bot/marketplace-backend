package com.marketplace.backend.product;

import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        long price,
        List<String> images,
        int stock,
        VendorSummary vendor,
        String category,
        ReviewSummary reviews
) {

    public record VendorSummary(
            UUID id,
            String name,
            Double rating
    ) {
    }

    public record ReviewSummary(
            Double avgRating,
            long count
    ) {
    }
}