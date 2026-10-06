package com.marketplace.backend.delivery;

import com.marketplace.backend.order.SubOrder;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "delivery_tracking_events",
        indexes = {
                @Index(name = "idx_delivery_events_sub_order_id", columnList = "sub_order_id"),
                @Index(name = "idx_delivery_events_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class DeliveryTrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_order_id", nullable = false)
    private SubOrder subOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
