package com.marketplace.backend.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository
        extends JpaRepository<Coupon, UUID> {

    List<Coupon> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    Optional<Coupon> findByVendorIdAndCode(
            UUID vendorId,
            String code
    );

    boolean existsByVendorIdAndCode(
            UUID vendorId,
            String code
    );
}