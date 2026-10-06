package com.marketplace.backend.cart;

import java.util.List;
import java.util.UUID;

public record CartResponse(
        List<CartItemResponse> items,
        long subtotal
) {

    public record CartItemResponse(
            UUID productId,
            int quantity,
            long price,
            UUID vendorId
    ) {
    }
}