package com.marketplace.backend.payment;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PaymentInitializationRecoveryService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentService paymentService;

    public PaymentInitializationRecoveryService(
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentService paymentService
    ) {
        this.paymentAttemptRepository =
                paymentAttemptRepository;

        this.paymentService =
                paymentService;
    }

    public int recoverStaleAttempts(
            Instant cutoff,
            int batchSize
    ) {
        if (cutoff == null) {
            throw new IllegalArgumentException(
                    "Recovery cutoff is required"
            );
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "Recovery batch size must be positive"
            );
        }

        List<PaymentAttempt> candidates =
                paymentAttemptRepository
                        .findByStatusAndInitiatedAtLessThanEqualOrderByInitiatedAtAsc(
                                PaymentAttemptStatus.INITIATED,
                                cutoff,
                                PageRequest.of(
                                        0,
                                        batchSize
                                )
                        );

        int recovered = 0;

        for (PaymentAttempt candidate :
                candidates) {

            String reference =
                    candidate.getReference();

            if (reference == null ||
                    reference.isBlank()) {
                continue;
            }

            if (paymentService
                    .recoverInitiatedPaystackAttempt(
                            reference
                    )) {

                recovered++;
            }
        }

        return recovered;
    }
}