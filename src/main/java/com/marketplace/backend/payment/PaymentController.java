package com.marketplace.backend.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(
            PaymentService paymentService
    ) {
        this.paymentService = paymentService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(
                    value = "x-paystack-signature",
                    required = false
            )
            String signature,
            @RequestBody String rawBody
    ) {
        paymentService.handleWebhook(
                signature,
                rawBody
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/orders/{orderId}/retry")
    public ResponseEntity<PaymentRetryResponse> retryPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId
    ) {
        UUID userId =
                UUID.fromString(
                        jwt.getSubject()
                );

        return ResponseEntity.ok(
                paymentService.retryPaystackPayment(
                        orderId,
                        userId
                )
        );
    }
}