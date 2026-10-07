package com.marketplace.backend.finance;

import com.marketplace.backend.order.SubOrder;
import com.marketplace.backend.order.SubOrderRepository;
import com.marketplace.backend.payment.Payment;
import com.marketplace.backend.payment.PaymentRepository;
import com.marketplace.backend.payment.PaymentStatus;
import com.marketplace.backend.vendor.Vendor;
import com.marketplace.backend.vendor.VendorRepository;
import com.marketplace.backend.vendor.VendorStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class VendorFinanceService {

    private final VendorRepository vendorRepository;
    private final VendorFinanceConfigRepository financeConfigRepository;
    private final VendorPayoutRepository payoutRepository;
    private final SubOrderRepository subOrderRepository;
    private final PaymentRepository paymentRepository;

    public VendorFinanceService(
            VendorRepository vendorRepository,
            VendorFinanceConfigRepository financeConfigRepository,
            VendorPayoutRepository payoutRepository,
            SubOrderRepository subOrderRepository,
            PaymentRepository paymentRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.financeConfigRepository = financeConfigRepository;
        this.payoutRepository = payoutRepository;
        this.subOrderRepository = subOrderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public VendorFinanceResponse getVendorFinance(UUID userId) {
        Vendor vendor = getApprovedVendor(userId);

        VendorFinanceConfig config = financeConfigRepository
                .findByVendorId(vendor.getId())
                .orElseGet(() -> createDefaultConfig(vendor));

        List<SubOrder> subOrders =
                subOrderRepository.findByVendorIdOrderByCreatedAtDesc(vendor.getId());

        long grossSales = 0L;

        for (SubOrder subOrder : subOrders) {
            Payment payment = paymentRepository
                    .findByOrderId(subOrder.getOrder().getId())
                    .orElse(null);

            if (payment != null && payment.getStatus() == PaymentStatus.SUCCESS) {
                grossSales += subOrder.getTotalAmount();
            }
        }

        int commissionPercent = config.getCommissionPercent();
        long commissionAmount = (grossSales * commissionPercent) / 100L;
        long netEarnings = grossSales - commissionAmount;

        List<VendorPayout> payouts =
                payoutRepository.findByVendorIdOrderByCreatedAtDesc(vendor.getId());

        long totalPaidOut = payouts.stream()
                .filter(payout -> payout.getStatus() == PayoutStatus.PAID)
                .mapToLong(VendorPayout::getAmount)
                .sum();

        long availableForPayout = Math.max(netEarnings - totalPaidOut, 0L);

        List<VendorFinanceResponse.PayoutResponse> payoutResponses = payouts.stream()
                .map(payout -> new VendorFinanceResponse.PayoutResponse(
                        payout.getId(),
                        payout.getAmount(),
                        payout.getStatus(),
                        payout.getReference(),
                        payout.getPaidAt(),
                        payout.getCreatedAt()
                ))
                .toList();

        return new VendorFinanceResponse(
                grossSales,
                commissionPercent,
                commissionAmount,
                netEarnings,
                totalPaidOut,
                availableForPayout,
                config.getPayoutSchedule(),
                payoutResponses
        );
    }

    private VendorFinanceConfig createDefaultConfig(Vendor vendor) {
        VendorFinanceConfig config = new VendorFinanceConfig();
        config.setVendor(vendor);
        config.setCommissionPercent(0);
        config.setPayoutSchedule(PayoutSchedule.MANUAL);
        return financeConfigRepository.save(config);
    }

    private Vendor getApprovedVendor(UUID userId) {
        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vendor profile not found"
                ));

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vendor account is not approved"
            );
        }

        return vendor;
    }
}