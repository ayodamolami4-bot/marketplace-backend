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
        prefix = "payment.expiry",
        name = "enabled",
        havingValue = "true"
)
public class PaymentExpiryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PaymentExpiryScheduler.class
            );

    private final PaymentExpiryService paymentExpiryService;
    private final long expiryAgeMs;
    private final int batchSize;

    public PaymentExpiryScheduler(
            PaymentExpiryService paymentExpiryService,
            @Value("${payment.expiry.age-ms:1800000}")
            long expiryAgeMs,
            @Value("${payment.expiry.batch-size:100}")
            int batchSize
    ) {
        this.paymentExpiryService =
                paymentExpiryService;

        this.expiryAgeMs =
                expiryAgeMs;

        this.batchSize =
                batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${payment.expiry.fixed-delay-ms:60000}",
            initialDelayString =
                    "${payment.expiry.initial-delay-ms:60000}"
    )
    public void expireFailedPayments() {

        Instant cutoff =
                Instant.now()
                        .minusMillis(
                                expiryAgeMs
                        );

        try {

            int expired =
                    paymentExpiryService
                            .expireDuePayments(
                                    cutoff,
                                    batchSize
                            );

            if (expired > 0) {

                log.info(
                        "Expired {} unpaid Paystack order(s)",
                        expired
                );
            }

        } catch (Exception exception) {

            log.error(
                    "Payment expiry run failed",
                    exception
            );
        }
    }
}