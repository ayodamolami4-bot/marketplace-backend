package com.marketplace.backend.report;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VendorReportResponse(
        List<RevenuePoint> revenueOverTime,
        List<BestSeller> bestSellers,
        List<CategoryMix> categoryMix
) {

    public record RevenuePoint(
            LocalDate date,
            long revenue
    ) {
    }

    public record BestSeller(
            UUID productId,
            String productName,
            int quantitySold,
            long revenue
    ) {
    }

    public record CategoryMix(
            UUID categoryId,
            String categoryName,
            long revenue
    ) {
    }
}