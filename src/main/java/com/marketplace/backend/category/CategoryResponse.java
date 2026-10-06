package com.marketplace.backend.category;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        UUID parentId,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}