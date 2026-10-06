package com.marketplace.backend.vendor;

import java.util.UUID;

public record VendorDashboardResponse(
        UUID vendorId,
        String businessName,
        long productCount,
        long lowStockCount,
        long todayOrders,
        long todayRevenue
) {
}