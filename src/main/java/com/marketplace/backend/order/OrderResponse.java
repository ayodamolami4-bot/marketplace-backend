package com.marketplace.backend.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String status,
        Instant createdAt,
        List<SubOrderResponse> subOrders
) {

    public record SubOrderResponse(
            UUID vendorId,
            String vendorName,
            String status,
            List<ItemResponse> items,
            String trackingNote
    ) {
    }

    public record ItemResponse(
            UUID productId,
            String productName,
            long unitPrice,
            int quantity,
            long subtotal
    ) {
    }
}