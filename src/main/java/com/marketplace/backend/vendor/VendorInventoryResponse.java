package com.marketplace.backend.vendor;

import java.util.List;

public record VendorInventoryResponse(
        List<VendorInventoryItemResponse> data,
        int page,
        int pageSize,
        long total
) {
}