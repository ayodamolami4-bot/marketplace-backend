package com.marketplace.backend.finance;

import com.marketplace.backend.vendor.Vendor;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "vendor_finance_configs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_vendor_finance_config_vendor_id",
                        columnNames = "vendor_id"
                )
        }
)
public class VendorFinanceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false, unique = true)
    private Vendor vendor;

    @Column(name = "commission_percent", nullable = false)
    private int commissionPercent = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_schedule", nullable = false, length = 20)
    private PayoutSchedule payoutSchedule = PayoutSchedule.MANUAL;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public VendorFinanceConfig() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public int getCommissionPercent() {
        return commissionPercent;
    }

    public void setCommissionPercent(int commissionPercent) {
        this.commissionPercent = commissionPercent;
    }

    public PayoutSchedule getPayoutSchedule() {
        return payoutSchedule;
    }

    public void setPayoutSchedule(PayoutSchedule payoutSchedule) {
        this.payoutSchedule = payoutSchedule;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}