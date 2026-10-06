package com.marketplace.backend.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository
        extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByTransactionReference(
            String transactionReference
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM Payment p
            WHERE p.transactionReference = :reference
            """)
    Optional<Payment> findByTransactionReferenceForUpdate(
            @Param("reference") String reference
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM Payment p
            WHERE p.id = :id
            """)
    Optional<Payment> findByIdForUpdate(
            @Param("id") UUID id
    );

    @Query("""
            SELECT p
            FROM Payment p
            WHERE p.method = :method
              AND p.status IN :statuses
              AND p.transactionReference IS NOT NULL
            ORDER BY p.createdAt ASC
            """)
    List<Payment> findReconciliationCandidates(
            @Param("method") PaymentMethod method,
            @Param("statuses") List<PaymentStatus> statuses,
            Pageable pageable
    );
}