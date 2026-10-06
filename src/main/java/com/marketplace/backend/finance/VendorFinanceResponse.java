package com.marketplace.backend.finance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VendorFinanceResponse(
        long grossSales,
        int commissionPercent,
        long commissionAmount,
        long netEarnings,
        long totalPaidOut,
        long availableForPayout,
        PayoutSchedule payoutSchedule,
        List<PayoutResponse> payouts
) {

    public record PayoutResponse(
            UUID id,
            long amount,
            PayoutStatus status,
            String reference,
            Instant paidAt,
            Instant createdAt
    ) {
    }
}