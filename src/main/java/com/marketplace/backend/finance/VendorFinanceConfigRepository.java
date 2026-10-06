package com.marketplace.backend.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VendorFinanceConfigRepository extends JpaRepository<VendorFinanceConfig, UUID> {

    Optional<VendorFinanceConfig> findByVendorId(UUID vendorId);
}