package com.marketplace.backend.admin;

import java.util.List;
import java.util.UUID;

public record AdminReportResponse(
        long totalSales,
        long totalOrders,
        List<VendorPerformance> vendorPerformance,
        List<CategoryBreakdown> categoryBreakdown
) {

    public record VendorPerformance(
            UUID vendorId,
            String vendorName,
            long revenue,
            long orders
    ) {
    }

    public record CategoryBreakdown(
            UUID categoryId,
            String categoryName,
            long revenue
    ) {
    }
}