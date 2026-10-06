package com.marketplace.backend.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(
        prefix = "payment.initialization-recovery",
        name = "enabled",
        havingValue = "true"
)
public class PaymentInitializationRecoveryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PaymentInitializationRecoveryScheduler.class
            );

    private final PaymentInitializationRecoveryService recoveryService;
    private final long recoveryAgeMs;
    private final int batchSize;

    public PaymentInitializationRecoveryScheduler(
            PaymentInitializationRecoveryService recoveryService,
            @Value(
                    "${payment.initialization-recovery.age-ms:120000}"
            )
            long recoveryAgeMs,
            @Value(
                    "${payment.initialization-recovery.batch-size:100}"
            )
            int batchSize
    ) {
        this.recoveryService =
                recoveryService;

        this.recoveryAgeMs =
                recoveryAgeMs;

        this.batchSize =
                batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${payment.initialization-recovery.fixed-delay-ms:60000}",
            initialDelayString =
                    "${payment.initialization-recovery.initial-delay-ms:60000}"
    )
    public void recoverStaleInitializations() {

        Instant cutoff =
                Instant.now()
                        .minusMillis(
                                recoveryAgeMs
                        );

        try {
            int recovered =
                    recoveryService
                            .recoverStaleAttempts(
                                    cutoff,
                                    batchSize
                            );

            if (recovered > 0) {
                log.info(
                        "Recovered {} stale Paystack initialization attempt(s)",
                        recovered
                );
            }

        } catch (Exception exception) {

            log.error(
                    "Paystack initialization recovery failed",
                    exception
            );
        }
    }
}