package com.marketplace.backend.payment;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Profile("demo")
@RestController
@RequestMapping("/api/v1/payments/demo")
public class DemoPaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public DemoPaymentController(
            PaymentRepository paymentRepository,
            PaymentService paymentService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @PostMapping("/{reference}/success")
    @Transactional
    public ResponseEntity<Map<String, Object>> succeed(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reference
    ) {
        UUID userId =
                UUID.fromString(
                        jwt.getSubject()
                );

        Payment payment =
                paymentRepository
                        .findByTransactionReference(
                                reference
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Payment not found"
                                )
                        );

        if (!payment.getOrder()
                .getUser()
                .getId()
                .equals(userId)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot complete this payment"
            );
        }

        if (payment.getMethod() !=
                PaymentMethod.PAYSTACK) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order is not a Paystack payment"
            );
        }

        long amount = payment.getAmount();

        paymentService.applyVerifiedSuccess(
                reference,
                amount
        );

        return ResponseEntity.ok(
                Map.of(
                        "reference",
                        reference,
                        "status",
                        "success",
                        "amount",
                        amount
                )
        );
    }
}