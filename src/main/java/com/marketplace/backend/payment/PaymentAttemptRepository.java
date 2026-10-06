package com.marketplace.backend.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT pa
        FROM PaymentAttempt pa
        WHERE pa.reference = :reference
        """)
    Optional<PaymentAttempt> findByReferenceForUpdate(
            @Param("reference") String reference
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT pa
        FROM PaymentAttempt pa
        WHERE pa.id = :id
        """)
    Optional<PaymentAttempt> findByIdForUpdate(
            @Param("id") UUID id
    );

    @Query("""
        SELECT pa
        FROM PaymentAttempt pa
        WHERE pa.payment.id = :paymentId
        ORDER BY pa.attemptNumber DESC
        """)
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberDesc(
            @Param("paymentId") UUID paymentId
    );

    @Query("""
        SELECT pa
        FROM PaymentAttempt pa
        WHERE pa.payment.id = :paymentId
        ORDER BY pa.attemptNumber DESC
        """)
    List<PaymentAttempt> findLatestByPaymentId(
            @Param("paymentId") UUID paymentId,
            Pageable pageable
    );

    @Query("""
        SELECT pa
        FROM PaymentAttempt pa
        WHERE pa.status IN :statuses
        ORDER BY pa.initiatedAt ASC
        """)
    List<PaymentAttempt> findRecoveryCandidates(
            @Param("statuses") List<PaymentAttemptStatus> statuses,
            Pageable pageable
    );

    @Query("""
        SELECT COALESCE(MAX(pa.attemptNumber), 0)
        FROM PaymentAttempt pa
        WHERE pa.payment.id = :paymentId
        """)
    int findMaxAttemptNumber(
            @Param("paymentId") UUID paymentId
    );
    List<PaymentAttempt> findByStatusAndFailedAtLessThanEqualOrderByFailedAtAsc(
            PaymentAttemptStatus status,
            Instant failedAt,
            Pageable pageable
    );
    List<PaymentAttempt> findByStatusAndInitiatedAtLessThanEqualOrderByInitiatedAtAsc(
            PaymentAttemptStatus status,
            Instant initiatedAt,
            Pageable pageable
    );
}