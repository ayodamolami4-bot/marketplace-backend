package com.marketplace.backend.order;

import com.marketplace.backend.vendor.Vendor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "sub_orders",
        indexes = {
                @Index(name = "idx_sub_orders_order_id", columnList = "order_id"),
                @Index(name = "idx_sub_orders_vendor_id", columnList = "vendor_id"),
                @Index(name = "idx_sub_orders_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SubOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false, unique = true, length = 40)
    private String subOrderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(nullable = false)
    private long subtotal;

    @Column(nullable = false)
    private long shippingFee;

    @Column(nullable = false)
    private long discountAmount;

    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

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
}