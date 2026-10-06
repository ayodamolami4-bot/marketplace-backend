package com.marketplace.backend.wishlist;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WishlistRequest(
        @NotNull UUID productId
) {
}