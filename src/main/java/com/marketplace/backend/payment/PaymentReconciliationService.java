package com.marketplace.backend.payment;

import com.marketplace.backend.payment.paystackclient.PaystackClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(
        prefix = "payment.reconciliation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentReconciliationService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PaymentReconciliationService.class
            );

    private final PaymentRepository paymentRepository;
    private final PaystackClient paystackClient;
    private final PaymentService paymentService;

    private final String paystackSecretKey;
    private final int batchSize;

    public PaymentReconciliationService(
            PaymentRepository paymentRepository,
            PaystackClient paystackClient,
            PaymentService paymentService,
            @Value("${paystack.secret-key:}") String paystackSecretKey,
            @Value("${payment.reconciliation.batch-size:100}")
            int batchSize
    ) {
        this.paymentRepository = paymentRepository;
        this.paystackClient = paystackClient;
        this.paymentService = paymentService;
        this.paystackSecretKey = paystackSecretKey;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${payment.reconciliation.fixed-delay-ms:60000}",
            initialDelayString =
                    "${payment.reconciliation.initial-delay-ms:30000}"
    )
    public void reconcilePendingPayments() {
        if (paystackSecretKey == null ||
                paystackSecretKey.isBlank()) {
            return;
        }

        if (batchSize <= 0) {
            log.warn(
                    "Payment reconciliation skipped because batch size is invalid"
            );
            return;
        }

        List<Payment> candidates =
                paymentRepository
                        .findReconciliationCandidates(
                                PaymentMethod.PAYSTACK,
                                List.of(
                                        PaymentStatus.PENDING,
                                        PaymentStatus.PROCESSING
                                ),
                                PageRequest.of(
                                        0,
                                        batchSize
                                )
                        );

        for (Payment payment : candidates) {
            reconcilePayment(payment);
        }
    }

    private void reconcilePayment(
            Payment payment
    ) {
        String reference =
                payment.getTransactionReference();

        if (reference == null ||
                reference.isBlank()) {
            return;
        }

        try {
            PaystackClient.VerificationResult result =
                    paystackClient.verifyTransaction(
                            reference
                    );

            if (!reference.equals(
                    result.reference()
            )) {
                log.warn(
                        "Paystack reference mismatch during reconciliation: expected={}, actual={}",
                        reference,
                        result.reference()
                );
                return;
            }

            if (result.amount() !=
                    payment.getAmount()) {
                log.error(
                        "Paystack amount mismatch during reconciliation: reference={}, expected={}, actual={}",
                        reference,
                        payment.getAmount(),
                        result.amount()
                );
                return;
            }

            if ("success".equalsIgnoreCase(
                    result.status()
            )) {
                paymentService.applyVerifiedSuccess(
                        reference,
                        result.amount()
                );

                log.info(
                        "Payment reconciled successfully: reference={}",
                        reference
                );

                return;
            }

            if (isTerminalFailure(
                    result.status()
            )) {
                paymentService.releaseStockForFailedPayment(
                        reference,
                        result.amount(),
                        result.status()
                );

                log.info(
                        "Failed payment recovered: reference={}, status={}",
                        reference,
                        result.status()
                );

                return;
            }

            log.debug(
                    "Payment still unresolved: reference={}, status={}",
                    reference,
                    result.status()
            );

        } catch (Exception exception) {
            log.warn(
                    "Payment reconciliation failed: reference={}, message={}",
                    reference,
                    exception.getMessage()
            );
        }
    }

    private boolean isTerminalFailure(
            String providerStatus
    ) {
        if (providerStatus == null) {
            return false;
        }

        return switch (
                providerStatus.trim().toLowerCase()
                ) {
            case "failed",
                 "abandoned",
                 "reversed" ->
                    true;

            default ->
                    false;
        };
    }
}