package com.marketplace.backend.vendor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VendorStockUpdateRequest(
        @NotNull
        @Min(0)
        Integer stock
) {
}