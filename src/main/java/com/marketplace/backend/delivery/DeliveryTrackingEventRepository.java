package com.marketplace.backend.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryTrackingEventRepository
        extends JpaRepository<DeliveryTrackingEvent, UUID> {

    List<DeliveryTrackingEvent> findBySubOrderIdOrderByCreatedAtAsc(UUID subOrderId);
}