package com.marketplace.backend.checkout;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckoutResponse(
        UUID orderId,
        List<SubOrderResponse> subOrders,
        String paymentMethod,
        PaystackResponse paystack
) {

    public record SubOrderResponse(
            UUID vendorId,
            UUID vendorOrderId,
            String status,
            List<ItemResponse> items
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

    public record PaystackResponse(
            String authorizationUrl,
            String reference
    ) {
    }
}