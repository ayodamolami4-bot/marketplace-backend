package com.marketplace.backend.vendor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    Optional<Vendor> findByUserId(UUID userId);

    List<Vendor> findByStatusOrderByCreatedAtAsc(VendorStatus status);

    boolean existsByUserId(UUID userId);
}
