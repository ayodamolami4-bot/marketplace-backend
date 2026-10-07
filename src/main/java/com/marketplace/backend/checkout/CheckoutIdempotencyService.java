package com.marketplace.backend.checkout;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class CheckoutIdempotencyService {

    private final CheckoutIdempotencyRepository repository;
    private final JsonMapper objectMapper;

    public CheckoutIdempotencyService(
            CheckoutIdempotencyRepository repository,
            JsonMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required"
            );
        }

        String normalized = value.trim();

        if (normalized.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must not exceed 255 characters"
            );
        }

        return normalized;
    }

    public String hashRequest(
            UUID userId,
            CheckoutRequest request
    ) {
        String canonical =
                userId
                        + "|"
                        + String.valueOf(request.getAddressId())
                        + "|"
                        + normalize(
                        request.getDeliveryMethod()
                )
                        + "|"
                        + normalizePaymentMethod(
                        request.getPaymentMethod()
                ) + "|" + normalize(request.getCouponCode()).toUpperCase(Locale.ROOT);

        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(
                                    canonical.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

            return HexFormat.of().formatHex(digest);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to create checkout request hash",
                    exception
            );
        }
    }

    public Claim reserve(
            UUID userId,
            String idempotencyKey,
            String requestHash
    ) {
        int inserted =
                repository.insertIfAbsent(
                        UUID.randomUUID(),
                        userId,
                        idempotencyKey,
                        requestHash
                );

        CheckoutIdempotency record =
                repository
                        .findByUserIdAndIdempotencyKeyForUpdate(
                                userId,
                                idempotencyKey
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Checkout idempotency record could not be loaded"
                                )
                        );

        if (!requestHash.equals(
                record.getRequestHash()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with different checkout data"
            );
        }

        if (record.getStatus() ==
                CheckoutIdempotencyStatus.COMPLETED) {

            if (record.getResponseBody() == null ||
                    record.getResponseBody().isBlank()) {
                throw new IllegalStateException(
                        "Completed checkout has no stored response"
                );
            }

            return new Claim(
                    false,
                    readResponse(record.getResponseBody()),
                    record
            );
        }

        if (inserted == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Checkout request is already being processed"
            );
        }

        return new Claim(
                true,
                null,
                record
        );
    }

    @Transactional
    public void complete(
            UUID userId,
            String idempotencyKey,
            CheckoutResponse response
    ) {
        CheckoutIdempotency record =
                repository
                        .findByUserIdAndIdempotencyKeyForUpdate(
                                userId,
                                idempotencyKey
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Checkout idempotency record not found"
                                )
                        );

        record.setStatus(
                CheckoutIdempotencyStatus.COMPLETED
        );

        record.setResponseStatus(201);

        record.setResponseBody(
                writeResponse(response)
        );

        repository.save(record);
    }

    private String writeResponse(
            CheckoutResponse response
    ) {
        try {
            return objectMapper.writeValueAsString(
                    response
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to store checkout response",
                    exception
            );
        }
    }

    private CheckoutResponse readResponse(
            String responseBody
    ) {
        try {
            return objectMapper.readValue(
                    responseBody,
                    CheckoutResponse.class
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to read stored checkout response",
                    exception
            );
        }
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim();
    }

    private String normalizePaymentMethod(
            String value
    ) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Claim(
            boolean newRequest,
            CheckoutResponse replayResponse,
            CheckoutIdempotency record
    ) {
    }
}
