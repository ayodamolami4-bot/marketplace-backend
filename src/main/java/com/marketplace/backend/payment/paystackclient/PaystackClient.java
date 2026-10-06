package com.marketplace.backend.payment.paystackclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaystackClient {

    private final RestClient restClient;
    private final String secretKey;
    private final String callbackUrl;

    private final boolean demoMode;
    private final String demoAuthorizationBaseUrl;

    private final Map<String, DemoTransaction> demoTransactions =
            new ConcurrentHashMap<>();

    public PaystackClient(
            @Value("${paystack.base-url}") String baseUrl,
            @Value("${paystack.secret-key:}") String secretKey,
            @Value("${paystack.callback-url:}") String callbackUrl,
            @Value("${paystack.demo-mode:false}") boolean demoMode,
            @Value(
                    "${paystack.demo-authorization-base-url:http://localhost:5173/payment/demo}"
            )
            String demoAuthorizationBaseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.secretKey = secretKey;
        this.callbackUrl = callbackUrl;

        this.demoMode = demoMode;
        this.demoAuthorizationBaseUrl =
                demoAuthorizationBaseUrl;
    }

    public InitializationResult initializeTransaction(
            String email,
            long amount,
            String reference,
            Map<String, Object> metadata
    ) {
        if (demoMode) {
            return initializeDemoTransaction(
                    amount,
                    reference
            );
        }

        Map<String, Object> body = new HashMap<>();

        body.put("email", email);
        body.put("amount", amount);
        body.put("reference", reference);
        body.put("currency", "NGN");
        body.put("metadata", metadata);

        if (callbackUrl != null &&
                !callbackUrl.isBlank()) {
            body.put(
                    "callback_url",
                    callbackUrl
            );
        }

        try {
            Map<String, Object> response =
                    restClient.post()
                            .uri("/transaction/initialize")
                            .header(
                                    "Authorization",
                                    "Bearer " + secretKey
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(body)
                            .retrieve()
                            .body(
                                    new ParameterizedTypeReference<>() {
                                    }
                            );

            if (response == null ||
                    !Boolean.TRUE.equals(
                            response.get("status")
                    )) {
                throw new IllegalStateException(
                        "Paystack transaction initialization failed"
                );
            }

            Object dataObject =
                    response.get("data");

            if (!(dataObject instanceof Map<?, ?> data)) {
                throw new IllegalStateException(
                        "Invalid Paystack initialization response"
                );
            }

            Object authorizationUrl =
                    data.get("authorization_url");

            Object responseReference =
                    data.get("reference");

            if (!(authorizationUrl
                    instanceof String authorizationUrlValue)
                    ||
                    !(responseReference
                            instanceof String referenceValue)) {

                throw new IllegalStateException(
                        "Incomplete Paystack initialization response"
                );
            }

            return new InitializationResult(
                    authorizationUrlValue,
                    referenceValue
            );

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Unable to initialize Paystack transaction",
                    exception
            );
        }
    }

    public VerificationResult verifyTransaction(
            String reference
    ) {
        if (demoMode) {
            return verifyDemoTransaction(
                    reference
            );
        }

        if (secretKey == null ||
                secretKey.isBlank()) {
            throw new IllegalStateException(
                    "Paystack is not configured"
            );
        }

        try {
            Map<String, Object> response =
                    restClient.get()
                            .uri(
                                    "/transaction/verify/{reference}",
                                    reference
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + secretKey
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .retrieve()
                            .body(
                                    new ParameterizedTypeReference<>() {
                                    }
                            );

            if (response == null ||
                    !Boolean.TRUE.equals(
                            response.get("status")
                    )) {
                throw new IllegalStateException(
                        "Paystack transaction verification failed"
                );
            }

            Object dataObject =
                    response.get("data");

            if (!(dataObject instanceof Map<?, ?> data)) {
                throw new IllegalStateException(
                        "Invalid Paystack verification response"
                );
            }

            Object responseReference =
                    data.get("reference");

            Object transactionStatus =
                    data.get("status");

            Object transactionAmount =
                    data.get("amount");

            if (!(responseReference
                    instanceof String referenceValue)
                    ||
                    !(transactionStatus
                            instanceof String statusValue)
                    ||
                    !(transactionAmount
                            instanceof Number amountValue)) {

                throw new IllegalStateException(
                        "Incomplete Paystack verification response"
                );
            }

            return new VerificationResult(
                    referenceValue,
                    statusValue,
                    amountValue.longValue()
            );

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Unable to verify Paystack transaction",
                    exception
            );
        }
    }

    public VerificationResult markDemoTransactionSuccessful(
            String reference
    ) {
        if (!demoMode) {
            throw new IllegalStateException(
                    "Demo payment mode is disabled"
            );
        }

        DemoTransaction transaction =
                demoTransactions.get(reference);

        if (transaction == null) {
            throw new IllegalStateException(
                    "Demo transaction not found"
            );
        }

        DemoTransaction successful =
                new DemoTransaction(
                        transaction.amount(),
                        "success"
                );

        demoTransactions.put(
                reference,
                successful
        );

        return new VerificationResult(
                reference,
                successful.status(),
                successful.amount()
        );
    }

    private InitializationResult initializeDemoTransaction(
            long amount,
            String reference
    ) {
        demoTransactions.put(
                reference,
                new DemoTransaction(
                        amount,
                        "pending"
                )
        );

        String separator =
                demoAuthorizationBaseUrl.contains("?")
                        ? "&"
                        : "?";

        String authorizationUrl =
                demoAuthorizationBaseUrl
                        + separator
                        + "reference="
                        + reference;

        return new InitializationResult(
                authorizationUrl,
                reference
        );
    }

    private VerificationResult verifyDemoTransaction(
            String reference
    ) {
        DemoTransaction transaction =
                demoTransactions.get(reference);

        if (transaction == null) {
            throw new IllegalStateException(
                    "Demo transaction not found"
            );
        }

        return new VerificationResult(
                reference,
                transaction.status(),
                transaction.amount()
        );
    }

    public record InitializationResult(
            String authorizationUrl,
            String reference
    ) {
    }

    public record VerificationResult(
            String reference,
            String status,
            long amount
    ) {
    }

    private record DemoTransaction(
            long amount,
            String status
    ) {
    }
}