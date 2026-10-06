package com.marketplace.backend.vendor;

import com.marketplace.backend.product.ProductStatus;

import java.util.UUID;

public record VendorInventoryItemResponse(
        UUID id,
        String name,
        long price,
        int stock,
        ProductStatus status
) {
}