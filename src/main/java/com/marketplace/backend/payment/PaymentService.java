package com.marketplace.backend.payment;

import com.marketplace.backend.common.DatabaseLockRetryExecutor;
import com.marketplace.backend.order.Order;
import com.marketplace.backend.order.OrderItem;
import com.marketplace.backend.order.OrderItemRepository;
import com.marketplace.backend.order.OrderRepository;
import com.marketplace.backend.order.OrderStatus;
import com.marketplace.backend.order.SubOrder;
import com.marketplace.backend.order.SubOrderRepository;
import com.marketplace.backend.payment.paystackclient.PaystackClient;
import com.marketplace.backend.product.Product;
import com.marketplace.backend.product.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final PaystackClient paystackClient;
    private final JsonMapper objectMapper;
    private final DatabaseLockRetryExecutor lockRetryExecutor;
    private final String paystackSecretKey;

    @Autowired
    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            OrderRepository orderRepository,
            SubOrderRepository subOrderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            PaystackClient paystackClient,
            JsonMapper objectMapper,
            DatabaseLockRetryExecutor lockRetryExecutor,
            @Value("${paystack.secret-key:}")
            String paystackSecretKey
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.orderRepository = orderRepository;
        this.subOrderRepository = subOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.paystackClient = paystackClient;
        this.objectMapper = objectMapper;
        this.lockRetryExecutor = lockRetryExecutor;
        this.paystackSecretKey = paystackSecretKey;
    }

    /*
     * Test-compatible constructor.
     *
     * Existing tests that directly instantiate PaymentService
     * with PlatformTransactionManager can remain unchanged.
     */
    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            OrderRepository orderRepository,
            SubOrderRepository subOrderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            PaystackClient paystackClient,
            JsonMapper objectMapper,
            PlatformTransactionManager transactionManager,
            String paystackSecretKey
    ) {
        this(
                paymentRepository,
                paymentAttemptRepository,
                orderRepository,
                subOrderRepository,
                orderItemRepository,
                productRepository,
                paystackClient,
                objectMapper,
                new DatabaseLockRetryExecutor(
                        transactionManager,
                        3,
                        0,
                        0
                ),
                paystackSecretKey
        );
    }

    public void validatePaystackConfiguration() {

        if (paystackSecretKey == null ||
                paystackSecretKey.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Paystack checkout is not configured"
            );
        }
    }

    /*
     * DB transaction #1:
     * create a durable PaymentAttempt.
     *
     * Paystack HTTP call happens AFTER this transaction commits.
     *
     * DB transaction #2:
     * persist Paystack initialization result.
     */
    public PaystackClient.InitializationResult initializePaystackPayment(
            String customerEmail,
            long amount,
            String orderNumber,
            UUID orderId,
            UUID paymentId,
            String ignoredTransactionReference
    ) {

        validatePaystackConfiguration();

        PaymentAttemptContext prepared =
                lockRetryExecutor.execute(
                        "prepare-payment-attempt-" + paymentId,
                        () ->
                                preparePaymentAttempt(
                                        amount,
                                        orderNumber,
                                        orderId,
                                        paymentId
                                )
                );

        Map<String, Object> metadata =
                new HashMap<>();

        metadata.put(
                "order_id",
                orderId.toString()
        );

        metadata.put(
                "payment_id",
                paymentId.toString()
        );

        metadata.put(
                "attempt_id",
                prepared.attemptId().toString()
        );

        metadata.put(
                "attempt_number",
                prepared.attemptNumber()
        );

        try {

            /*
             * EXTERNAL NETWORK CALL.
             *
             * Deliberately outside DatabaseLockRetryExecutor.
             *
             * We never automatically repeat this call because
             * a timeout does not prove Paystack did not receive it.
             */
            PaystackClient.InitializationResult result =
                    paystackClient.initializeTransaction(
                            customerEmail,
                            amount,
                            prepared.reference(),
                            metadata
                    );

            if (result == null ||
                    result.reference() == null ||
                    !prepared.reference()
                            .equals(result.reference())) {

                lockRetryExecutor.executeWithoutResult(
                        "record-payment-reference-error-"
                                + prepared.reference(),
                        () ->
                                recordInitializationFailure(
                                        prepared.reference(),
                                        "Paystack returned an unexpected reference"
                                )
                );

                throw new IllegalStateException(
                        "Paystack returned an unexpected transaction reference"
                );
            }

            lockRetryExecutor.executeWithoutResult(
                    "mark-payment-attempt-pending-"
                            + prepared.reference(),
                    () ->
                            markAttemptPending(
                                    prepared.reference(),
                                    result.authorizationUrl()
                            )
            );

            return result;

        } catch (RuntimeException exception) {

            /*
             * Do NOT mark the attempt FAILED here.
             *
             * Example:
             * - Paystack receives request
             * - Paystack creates transaction
             * - response is lost
             *
             * We cannot safely conclude payment initialization
             * failed.
             */
            lockRetryExecutor.executeWithoutResult(
                    "record-payment-initialization-failure-"
                            + prepared.reference(),
                    () ->
                            recordInitializationFailure(
                                    prepared.reference(),
                                    safeMessage(exception)
                            )
            );

            throw exception;
        }
    }

    public PaymentRetryResponse retryPaystackPayment(
            UUID orderId,
            UUID userId
    ) {

        PaymentRetryContext context =
                lockRetryExecutor.execute(
                        "validate-payment-retry-" + orderId,
                        () -> {

                            Order order =
                                    orderRepository
                                            .findByIdForUpdate(
                                                    orderId
                                            )
                                            .orElseThrow(() ->
                                                    new ResponseStatusException(
                                                            HttpStatus.NOT_FOUND,
                                                            "Order not found"
                                                    )
                                            );

                            if (order.getUser() == null ||
                                    !order.getUser()
                                            .getId()
                                            .equals(userId)) {

                                throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "You cannot retry payment for this order"
                                );
                            }

                            if (order.getStatus() !=
                                    OrderStatus.PENDING_PAYMENT) {

                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Order is not awaiting payment"
                                );
                            }

                            if (order.getStockReleasedAt() != null) {

                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Order payment window has expired"
                                );
                            }

                            Payment payment =
                                    paymentRepository
                                            .findByOrderId(
                                                    orderId
                                            )
                                            .orElseThrow(() ->
                                                    new ResponseStatusException(
                                                            HttpStatus.NOT_FOUND,
                                                            "Payment not found"
                                                    )
                                            );

                            payment =
                                    paymentRepository
                                            .findByIdForUpdate(
                                                    payment.getId()
                                            )
                                            .orElseThrow(() ->
                                                    new ResponseStatusException(
                                                            HttpStatus.NOT_FOUND,
                                                            "Payment not found"
                                                    )
                                            );

                            if (payment.getMethod() !=
                                    PaymentMethod.PAYSTACK) {

                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Only Paystack payments can be retried"
                                );
                            }

                            if (payment.getStatus() ==
                                    PaymentStatus.SUCCESS) {

                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Payment has already succeeded"
                                );
                            }

                            if (payment.getStatus() ==
                                    PaymentStatus.PROCESSING) {

                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Payment initialization is still being resolved"
                                );
                            }

                            List<PaymentAttempt> attempts =
                                    paymentAttemptRepository
                                            .findByPaymentIdOrderByAttemptNumberDesc(
                                                    payment.getId()
                                            );

                            /*
                             * PENDING + a real PaymentAttempt means
                             * there is already an active Paystack
                             * transaction.
                             */
                            if (payment.getStatus() ==
                                    PaymentStatus.PENDING &&
                                    !attempts.isEmpty()) {

                                PaymentAttempt latest =
                                        attempts.getFirst();

                                if (latest.getStatus() ==
                                        PaymentAttemptStatus.INITIATED ||
                                        latest.getStatus() ==
                                                PaymentAttemptStatus.PENDING) {

                                    throw new ResponseStatusException(
                                            HttpStatus.CONFLICT,
                                            "A payment attempt is already active"
                                    );
                                }
                            }

                            if (payment.getStatus() !=
                                    PaymentStatus.FAILED &&
                                    payment.getStatus() !=
                                            PaymentStatus.PENDING) {

                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Payment cannot currently be retried"
                                );
                            }

                            String email =
                                    order.getUser()
                                            .getEmail();

                            if (email == null ||
                                    email.isBlank()) {

                                throw new IllegalStateException(
                                        "Customer email is missing"
                                );
                            }

                            return new PaymentRetryContext(
                                    email,
                                    payment.getAmount(),
                                    order.getOrderNumber(),
                                    order.getId(),
                                    payment.getId()
                            );
                        }
                );

        PaystackClient.InitializationResult result =
                initializePaystackPayment(
                        context.customerEmail(),
                        context.amount(),
                        context.orderNumber(),
                        context.orderId(),
                        context.paymentId(),
                        null
                );

        return new PaymentRetryResponse(
                result.authorizationUrl(),
                result.reference()
        );
    }

    /*
     * Handles:
     *
     * DB committed INITIATED attempt
     * -> application crashed
     * -> Paystack call never completed locally.
     */
    public boolean recoverInitiatedPaystackAttempt(
            String reference
    ) {

        validatePaystackConfiguration();

        ExistingAttemptContext context =
                lockRetryExecutor.execute(
                        "recover-payment-attempt-" + reference,
                        () -> {

                            PaymentAttempt attempt =
                                    paymentAttemptRepository
                                            .findByReferenceForUpdate(
                                                    reference
                                            )
                                            .orElse(null);

                            if (attempt == null ||
                                    attempt.getStatus() !=
                                            PaymentAttemptStatus.INITIATED) {

                                return null;
                            }

                            Payment payment =
                                    paymentRepository
                                            .findByIdForUpdate(
                                                    attempt.getPayment()
                                                            .getId()
                                            )
                                            .orElse(null);

                            if (payment == null ||
                                    payment.getMethod() !=
                                            PaymentMethod.PAYSTACK) {

                                return null;
                            }

                            if (payment.getStatus() ==
                                    PaymentStatus.SUCCESS) {

                                return null;
                            }

                            /*
                             * Never revive an old attempt after a
                             * newer retry has become current.
                             */
                            if (!reference.equals(
                                    payment.getTransactionReference()
                            )) {

                                return null;
                            }

                            if (payment.getStatus() !=
                                    PaymentStatus.PROCESSING) {

                                return null;
                            }

                            Order order =
                                    orderRepository
                                            .findByIdForUpdate(
                                                    payment.getOrder()
                                                            .getId()
                                            )
                                            .orElse(null);

                            if (order == null ||
                                    order.getStatus() !=
                                            OrderStatus.PENDING_PAYMENT ||
                                    order.getStockReleasedAt() != null) {

                                return null;
                            }

                            if (order.getUser() == null ||
                                    order.getUser()
                                            .getEmail() == null ||
                                    order.getUser()
                                            .getEmail()
                                            .isBlank()) {

                                return null;
                            }

                            return new ExistingAttemptContext(
                                    order.getUser()
                                            .getEmail(),
                                    attempt.getAmount(),
                                    order.getId(),
                                    payment.getId(),
                                    reference
                            );
                        }
                );

        if (context == null) {
            return false;
        }

        Map<String, Object> metadata =
                new HashMap<>();

        metadata.put(
                "order_id",
                context.orderId().toString()
        );

        metadata.put(
                "payment_id",
                context.paymentId().toString()
        );

        try {

            /*
             * External Paystack call stays outside DB retry.
             */
            PaystackClient.InitializationResult result =
                    paystackClient.initializeTransaction(
                            context.customerEmail(),
                            context.amount(),
                            context.reference(),
                            metadata
                    );

            if (result == null ||
                    result.reference() == null ||
                    !context.reference()
                            .equals(result.reference())) {

                lockRetryExecutor.executeWithoutResult(
                        "record-payment-recovery-reference-error-"
                                + context.reference(),
                        () ->
                                recordInitializationFailure(
                                        context.reference(),
                                        "Recovery returned an unexpected Paystack reference"
                                )
                );

                return false;
            }

            lockRetryExecutor.executeWithoutResult(
                    "recover-payment-attempt-pending-"
                            + context.reference(),
                    () ->
                            markAttemptPending(
                                    context.reference(),
                                    result.authorizationUrl()
                            )
            );

            return true;

        } catch (RuntimeException exception) {

            /*
             * Still uncertain.
             *
             * Do not mark FAILED.
             * Reconciliation can later verify this reference.
             */
            lockRetryExecutor.executeWithoutResult(
                    "record-payment-recovery-failure-"
                            + context.reference(),
                    () ->
                            recordInitializationFailure(
                                    context.reference(),
                                    safeMessage(exception)
                            )
            );

            return false;
        }
    }

    private PaymentAttemptContext preparePaymentAttempt(
            long amount,
            String orderNumber,
            UUID orderId,
            UUID paymentId
    ) {

        Order order =
                orderRepository
                        .findByIdForUpdate(
                                orderId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Order not found"
                                )
                        );

        Payment payment =
                paymentRepository
                        .findByIdForUpdate(
                                paymentId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Payment not found"
                                )
                        );

        if (!payment.getOrder()
                .getId()
                .equals(orderId)) {

            throw new IllegalStateException(
                    "Payment does not belong to order"
            );
        }

        if (payment.getMethod() !=
                PaymentMethod.PAYSTACK) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment is not a Paystack payment"
            );
        }

        if (order.getStatus() !=
                OrderStatus.PENDING_PAYMENT) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order is not awaiting payment"
            );
        }

        if (order.getStockReleasedAt() != null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order payment window has expired"
            );
        }

        if (payment.getStatus() ==
                PaymentStatus.SUCCESS) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment has already succeeded"
            );
        }

        if (payment.getStatus() ==
                PaymentStatus.PROCESSING) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment initialization is already in progress"
            );
        }

        if (payment.getStatus() !=
                PaymentStatus.PENDING &&
                payment.getStatus() !=
                        PaymentStatus.FAILED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment cannot be initialized"
            );
        }

        if (payment.getAmount() != amount) {

            throw new IllegalStateException(
                    "Payment amount does not match checkout amount"
            );
        }

        Integer currentMax =
                paymentAttemptRepository
                        .findMaxAttemptNumber(
                                paymentId
                        );

        int attemptNumber =
                currentMax == null
                        ? 1
                        : currentMax + 1;

        String reference =
                buildAttemptReference(
                        orderNumber,
                        attemptNumber
                );

        Instant now =
                Instant.now();

        PaymentAttempt attempt =
                new PaymentAttempt();

        attempt.setId(
                UUID.randomUUID()
        );

        attempt.setPayment(
                payment
        );

        attempt.setAttemptNumber(
                attemptNumber
        );

        attempt.setProvider(
                "PAYSTACK"
        );

        attempt.setReference(
                reference
        );

        attempt.setAmount(
                amount
        );

        attempt.setStatus(
                PaymentAttemptStatus.INITIATED
        );

        attempt.setInitiatedAt(
                now
        );

        attempt =
                paymentAttemptRepository.save(
                        attempt
                );

        payment.setTransactionReference(
                reference
        );

        payment.setStatus(
                PaymentStatus.PROCESSING
        );

        paymentRepository.save(
                payment
        );

        return new PaymentAttemptContext(
                attempt.getId(),
                attemptNumber,
                reference
        );
    }

    private void markAttemptPending(
            String reference,
            String authorizationUrl
    ) {

        PaymentAttempt attempt =
                paymentAttemptRepository
                        .findByReferenceForUpdate(
                                reference
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Payment attempt not found: "
                                                + reference
                                )
                        );

        if (attempt.getStatus() ==
                PaymentAttemptStatus.SUCCESS ||
                attempt.getStatus() ==
                        PaymentAttemptStatus.FAILED ||
                attempt.getStatus() ==
                        PaymentAttemptStatus.EXPIRED) {

            return;
        }

        attempt.setStatus(
                PaymentAttemptStatus.PENDING
        );

        attempt.setAuthorizationUrl(
                authorizationUrl
        );

        attempt.setLastError(
                null
        );

        paymentAttemptRepository.save(
                attempt
        );

        Payment payment =
                paymentRepository
                        .findByIdForUpdate(
                                attempt.getPayment()
                                        .getId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Payment not found for attempt"
                                )
                        );

        /*
         * Only the currently active reference can move the
         * parent payment back to PENDING.
         */
        if (reference.equals(
                payment.getTransactionReference()
        ) &&
                payment.getStatus() !=
                        PaymentStatus.SUCCESS) {

            payment.setStatus(
                    PaymentStatus.PENDING
            );

            paymentRepository.save(
                    payment
            );
        }
    }

    private void recordInitializationFailure(
            String reference,
            String message
    ) {

        PaymentAttempt attempt =
                paymentAttemptRepository
                        .findByReferenceForUpdate(
                                reference
                        )
                        .orElse(null);

        if (attempt == null) {
            return;
        }

        if (attempt.getStatus() ==
                PaymentAttemptStatus.SUCCESS ||
                attempt.getStatus() ==
                        PaymentAttemptStatus.FAILED ||
                attempt.getStatus() ==
                        PaymentAttemptStatus.EXPIRED) {

            return;
        }

        /*
         * Deliberately remain INITIATED/PENDING.
         *
         * A transport exception is uncertain, not a
         * confirmed provider failure.
         */
        attempt.setLastError(
                message
        );

        paymentAttemptRepository.save(
                attempt
        );
    }

    @Transactional
    public void applyVerifiedSuccess(
            String reference,
            long amount
    ) {

        PaymentAttempt attempt =
                paymentAttemptRepository
                        .findByReferenceForUpdate(
                                reference
                        )
                        .orElse(null);

        Payment payment;

        if (attempt != null) {

            payment =
                    paymentRepository
                            .findByIdForUpdate(
                                    attempt.getPayment()
                                            .getId()
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Payment not found"
                                    )
                            );

        } else {

            /*
             * Compatibility for old payments created before
             * payment_attempts existed.
             */
            payment =
                    paymentRepository
                            .findByTransactionReferenceForUpdate(
                                    reference
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Payment not found"
                                    )
                            );
        }

        if (amount != payment.getAmount()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount mismatch"
            );
        }

        if (payment.getStatus() ==
                PaymentStatus.SUCCESS) {

            if (attempt != null &&
                    attempt.getStatus() !=
                            PaymentAttemptStatus.SUCCESS) {

                attempt.setStatus(
                        PaymentAttemptStatus.SUCCESS
                );

                attempt.setVerifiedAt(
                        Instant.now()
                );

                paymentAttemptRepository.save(
                        attempt
                );
            }

            return;
        }

        if (payment.getStatus() !=
                PaymentStatus.PENDING &&
                payment.getStatus() !=
                        PaymentStatus.PROCESSING &&
                payment.getStatus() !=
                        PaymentStatus.FAILED) {

            return;
        }

        if (attempt != null) {

            attempt.setStatus(
                    PaymentAttemptStatus.SUCCESS
            );

            attempt.setVerifiedAt(
                    Instant.now()
            );

            attempt.setLastError(
                    null
            );

            paymentAttemptRepository.save(
                    attempt
            );

            /*
             * A previous attempt can succeed late.
             *
             * Paystack is authoritative, so that reference
             * becomes canonical and settles the order.
             */
            payment.setTransactionReference(
                    reference
            );
        }

        markPaymentSuccessful(
                payment
        );
    }

    @Transactional
    public void releaseStockForFailedPayment(
            String reference,
            long amount,
            String providerStatus
    ) {

        if (!isTerminalFailure(
                providerStatus
        )) {
            return;
        }

        /*
         * New payment-attempt flow.
         *
         * One failed attempt does NOT cancel the order and
         * does NOT restore inventory. The customer may retry.
         */
        PaymentAttempt attempt =
                paymentAttemptRepository
                        .findByReferenceForUpdate(
                                reference
                        )
                        .orElse(null);

        if (attempt != null) {

            Payment payment =
                    paymentRepository
                            .findByIdForUpdate(
                                    attempt.getPayment()
                                            .getId()
                            )
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Payment not found during failure recovery"
                                    )
                            );

            if (payment.getMethod() !=
                    PaymentMethod.PAYSTACK) {

                return;
            }

            if (amount != payment.getAmount()) {

                throw new IllegalStateException(
                        "Paystack amount mismatch during payment failure recovery"
                );
            }

            if (attempt.getStatus() ==
                    PaymentAttemptStatus.SUCCESS) {

                return;
            }

            attempt.setStatus(
                    PaymentAttemptStatus.FAILED
            );

            attempt.setFailedAt(
                    Instant.now()
            );

            attempt.setLastError(
                    providerStatus
            );

            paymentAttemptRepository.save(
                    attempt
            );

            /*
             * A successful payment always wins.
             */
            if (payment.getStatus() ==
                    PaymentStatus.SUCCESS) {

                return;
            }

            /*
             * Failure belonging to an older attempt cannot
             * alter the currently active newer attempt.
             */
            if (!reference.equals(
                    payment.getTransactionReference()
            )) {

                return;
            }

            payment.setStatus(
                    PaymentStatus.FAILED
            );

            paymentRepository.save(
                    payment
            );

            /*
             * DO NOT:
             * - cancel order
             * - release stock
             *
             * PaymentExpiryService handles final abandonment
             * after the retry window.
             */
            return;
        }

        /*
         * Legacy payment path.
         *
         * Existing tests and old database payments may not
         * have PaymentAttempt records.
         */
        releaseLegacyFailedPaymentStock(
                reference,
                amount
        );
    }

    private void releaseLegacyFailedPaymentStock(
            String reference,
            long amount
    ) {

        Payment payment =
                paymentRepository
                        .findByTransactionReferenceForUpdate(
                                reference
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Payment not found during failure recovery"
                                )
                        );

        if (payment.getMethod() !=
                PaymentMethod.PAYSTACK) {

            return;
        }

        if (payment.getStatus() ==
                PaymentStatus.SUCCESS) {

            return;
        }

        if (payment.getStatus() !=
                PaymentStatus.PENDING &&
                payment.getStatus() !=
                        PaymentStatus.PROCESSING) {

            return;
        }

        if (amount != payment.getAmount()) {

            throw new IllegalStateException(
                    "Paystack amount mismatch during payment failure recovery"
            );
        }

        Order order =
                orderRepository
                        .findByIdForUpdate(
                                payment.getOrder()
                                        .getId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Order not found during payment failure recovery"
                                )
                        );

        if (order.getStockReleasedAt() != null) {

            payment.setStatus(
                    PaymentStatus.FAILED
            );

            paymentRepository.save(
                    payment
            );

            if (order.getStatus() ==
                    OrderStatus.PENDING_PAYMENT) {

                order.setStatus(
                        OrderStatus.CANCELLED
                );

                orderRepository.save(
                        order
                );
            }

            cancelPendingSubOrders(
                    order.getId()
            );

            return;
        }

        if (order.getStatus() !=
                OrderStatus.PENDING_PAYMENT) {

            throw new IllegalStateException(
                    "Order is not pending payment during stock recovery"
            );
        }

        List<SubOrder> subOrders =
                subOrderRepository
                        .findByOrderId(
                                order.getId()
                        );

        List<UUID> subOrderIds =
                subOrders.stream()
                        .map(SubOrder::getId)
                        .toList();

        List<OrderItem> orderItems =
                subOrderIds.isEmpty()
                        ? List.of()
                        : orderItemRepository
                        .findBySubOrderIdIn(
                                subOrderIds
                        );

        List<OrderItem> orderedItems =
                new ArrayList<>(
                        orderItems
                );

        orderedItems.sort(
                Comparator.comparing(
                        item ->
                                item.getProduct()
                                        .getId()
                )
        );

        for (OrderItem orderItem :
                orderedItems) {

            UUID productId =
                    orderItem
                            .getProduct()
                            .getId();

            Product product =
                    productRepository
                            .findByIdForUpdate(
                                    productId
                            );

            if (product == null) {

                throw new IllegalStateException(
                        "Product not found during payment recovery: "
                                + productId
                );
            }

            int restoredStock;

            try {

                restoredStock =
                        Math.addExact(
                                product.getStockQuantity(),
                                orderItem.getQuantity()
                        );

            } catch (ArithmeticException exception) {

                throw new IllegalStateException(
                        "Stock overflow during payment recovery for product "
                                + productId,
                        exception
                );
            }

            product.setStockQuantity(
                    restoredStock
            );

            productRepository.save(
                    product
            );
        }

        order.setStockReleasedAt(
                Instant.now()
        );

        order.setStatus(
                OrderStatus.CANCELLED
        );

        orderRepository.save(
                order
        );

        for (SubOrder subOrder :
                subOrders) {

            if (subOrder.getStatus() ==
                    OrderStatus.PENDING_PAYMENT) {

                subOrder.setStatus(
                        OrderStatus.CANCELLED
                );

                subOrderRepository.save(
                        subOrder
                );
            }
        }

        payment.setStatus(
                PaymentStatus.FAILED
        );

        paymentRepository.save(
                payment
        );
    }

    private void cancelPendingSubOrders(
            UUID orderId
    ) {

        List<SubOrder> subOrders =
                subOrderRepository
                        .findByOrderId(
                                orderId
                        );

        for (SubOrder subOrder :
                subOrders) {

            if (subOrder.getStatus() ==
                    OrderStatus.PENDING_PAYMENT) {

                subOrder.setStatus(
                        OrderStatus.CANCELLED
                );

                subOrderRepository.save(
                        subOrder
                );
            }
        }
    }

    @Transactional
    public void handleWebhook(
            String signature,
            String rawBody
    ) {

        if (!verifySignature(
                signature,
                rawBody
        )) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid Paystack signature"
            );
        }

        final JsonNode payload;

        try {

            payload =
                    objectMapper.readTree(
                            rawBody
                    );

        } catch (Exception exception) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid webhook payload"
            );
        }

        String event =
                payload.path(
                        "event"
                ).asText();

        if (!"charge.success".equals(
                event
        )) {
            return;
        }

        JsonNode data =
                payload.path(
                        "data"
                );

        String reference =
                data.path(
                        "reference"
                ).asText(null);

        String status =
                data.path(
                        "status"
                ).asText(null);

        long amount =
                data.path(
                        "amount"
                ).asLong(-1);

        if (reference == null ||
                reference.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment reference is missing"
            );
        }

        if (!"success".equalsIgnoreCase(
                status
        )) {
            return;
        }

        applyVerifiedSuccess(
                reference,
                amount
        );
    }

    private void markPaymentSuccessful(
            Payment payment
    ) {

        Order order =
                orderRepository
                        .findByIdForUpdate(
                                payment.getOrder()
                                        .getId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Order not found while completing payment"
                                )
                        );

        /*
         * Inventory was already released because the payment
         * window expired. We cannot silently confirm the order.
         */
        if (order.getStockReleasedAt() != null) {

            throw new IllegalStateException(
                    "Payment succeeded after reserved stock was released"
            );
        }

        payment.setStatus(
                PaymentStatus.SUCCESS
        );

        payment.setPaidAt(
                Instant.now()
        );

        paymentRepository.save(
                payment
        );

        List<SubOrder> subOrders =
                subOrderRepository
                        .findByOrderId(
                                order.getId()
                        );

        for (SubOrder subOrder :
                subOrders) {

            if (subOrder.getStatus() ==
                    OrderStatus.PENDING_PAYMENT) {

                subOrder.setStatus(
                        OrderStatus.PENDING_FULFILLMENT
                );

                subOrderRepository.save(
                        subOrder
                );
            }
        }

        order.setStatus(
                OrderStatus.CONFIRMED
        );

        orderRepository.save(
                order
        );
    }

    private boolean isTerminalFailure(
            String providerStatus
    ) {

        if (providerStatus == null) {
            return false;
        }

        return switch (
                providerStatus
                        .trim()
                        .toLowerCase()
                ) {
            case "failed",
                 "abandoned",
                 "reversed" -> true;

            default -> false;
        };
    }

    private boolean verifySignature(
            String signature,
            String rawBody
    ) {

        if (signature == null ||
                signature.isBlank() ||
                paystackSecretKey == null ||
                paystackSecretKey.isBlank()) {

            return false;
        }

        try {

            Mac mac =
                    Mac.getInstance(
                            "HmacSHA512"
                    );

            SecretKeySpec key =
                    new SecretKeySpec(
                            paystackSecretKey.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "HmacSHA512"
                    );

            mac.init(
                    key
            );

            byte[] digest =
                    mac.doFinal(
                            rawBody.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder expected =
                    new StringBuilder();

            for (byte value :
                    digest) {

                expected.append(
                        String.format(
                                "%02x",
                                value
                        )
                );
            }

            return MessageDigest.isEqual(
                    expected.toString()
                            .getBytes(
                                    StandardCharsets.UTF_8
                            ),
                    signature.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

        } catch (Exception exception) {

            return false;
        }
    }

    private String buildAttemptReference(
            String orderNumber,
            int attemptNumber
    ) {

        String safeOrder =
                orderNumber == null ||
                        orderNumber.isBlank()
                        ? "ORDER"
                        : orderNumber
                        .replaceAll(
                                "[^A-Za-z0-9_-]",
                                "-"
                        );

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace(
                                "-",
                                ""
                        )
                        .substring(
                                0,
                                12
                        );

        return safeOrder
                + "-A"
                + attemptNumber
                + "-"
                + suffix;
    }

    private String safeMessage(
            RuntimeException exception
    ) {

        if (exception.getMessage() == null ||
                exception.getMessage()
                        .isBlank()) {

            return exception.getClass()
                    .getSimpleName();
        }

        return exception.getMessage();
    }

    private record PaymentAttemptContext(
            UUID attemptId,
            int attemptNumber,
            String reference
    ) {
    }

    private record PaymentRetryContext(
            String customerEmail,
            long amount,
            String orderNumber,
            UUID orderId,
            UUID paymentId
    ) {
    }

    private record ExistingAttemptContext(
            String customerEmail,
            long amount,
            UUID orderId,
            UUID paymentId,
            String reference
    ) {
    }
}