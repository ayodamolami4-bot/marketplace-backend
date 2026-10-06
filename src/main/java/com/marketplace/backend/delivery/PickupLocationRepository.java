package com.marketplace.backend.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PickupLocationRepository extends JpaRepository<PickupLocation, UUID> {

    List<PickupLocation> findByActiveTrueOrderByNameAsc();
}