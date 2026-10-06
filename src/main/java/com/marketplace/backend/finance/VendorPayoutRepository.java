package com.marketplace.backend.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendorPayoutRepository extends JpaRepository<VendorPayout, UUID> {

    List<VendorPayout> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    long countByVendorIdAndStatus(UUID vendorId, PayoutStatus status);
}