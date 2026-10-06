package com.marketplace.backend.checkout;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutIdempotencyRepository
        extends JpaRepository<CheckoutIdempotency, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO checkout_idempotency (
                        id,
                        user_id,
                        idempotency_key,
                        request_hash,
                        status,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        :id,
                        :userId,
                        :idempotencyKey,
                        :requestHash,
                        'PROCESSING',
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (
                        user_id,
                        idempotency_key
                    ) DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c
            FROM CheckoutIdempotency c
            WHERE c.userId = :userId
              AND c.idempotencyKey = :idempotencyKey
            """)
    Optional<CheckoutIdempotency>
    findByUserIdAndIdempotencyKeyForUpdate(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c
            FROM CheckoutIdempotency c
            WHERE c.id = :id
            """)
    Optional<CheckoutIdempotency> findByIdForUpdate(
            @Param("id") UUID id
    );

    @Query("""
            SELECT c
            FROM CheckoutIdempotency c
            WHERE c.status =
                com.marketplace.backend.checkout.CheckoutIdempotencyStatus.PROCESSING
              AND c.orderId IS NOT NULL
              AND c.updatedAt < :cutoff
            ORDER BY c.updatedAt ASC
            """)
    List<CheckoutIdempotency> findStaleProcessingWithOrder(
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );
}