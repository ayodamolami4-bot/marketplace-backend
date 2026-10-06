package com.marketplace.backend.checkout;

import com.marketplace.backend.order.Order;
import com.marketplace.backend.order.OrderItem;
import com.marketplace.backend.order.OrderItemRepository;
import com.marketplace.backend.order.OrderStatus;
import com.marketplace.backend.order.SubOrder;
import com.marketplace.backend.order.SubOrderRepository;
import com.marketplace.backend.payment.Payment;
import com.marketplace.backend.payment.PaymentMethod;
import com.marketplace.backend.payment.PaymentRepository;
import com.marketplace.backend.payment.PaymentStatus;
import com.marketplace.backend.order.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CheckoutIdempotencyRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    CheckoutIdempotencyRecoveryService.class
            );

    private final CheckoutIdempotencyRepository idempotencyRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SubOrderRepository subOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CheckoutIdempotencyService idempotencyService;

    private final long recoveryAgeMs;
    private final int batchSize;
    private final boolean enabled;

    public CheckoutIdempotencyRecoveryService(
            CheckoutIdempotencyRepository idempotencyRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            SubOrderRepository subOrderRepository,
            OrderItemRepository orderItemRepository,
            CheckoutIdempotencyService idempotencyService,
            @Value("${checkout.idempotency.recovery-age-ms:300000}")
            long recoveryAgeMs,
            @Value("${checkout.idempotency.recovery-batch-size:100}")
            int batchSize,
            @Value("${checkout.idempotency.recovery-enabled:true}")
            boolean enabled
    ) {
        this.idempotencyRepository = idempotencyRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.subOrderRepository = subOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.idempotencyService = idempotencyService;
        this.recoveryAgeMs = recoveryAgeMs;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    @Transactional
    @Scheduled(
            fixedDelayString = "${checkout.idempotency.recovery-fixed-delay-ms:60000}",
            initialDelayString = "${checkout.idempotency.recovery-initial-delay-ms:60000}"
    )
    public void recoverStaleProcessingCheckouts() {
        if (!enabled) {
            return;
        }

        if (recoveryAgeMs <= 0 || batchSize <= 0) {
            log.warn(
                    "Checkout idempotency recovery skipped because configuration is invalid"
            );
            return;
        }

        Instant cutoff =
                Instant.now().minusMillis(
                        recoveryAgeMs
                );

        List<CheckoutIdempotency> candidates =
                idempotencyRepository
                        .findStaleProcessingWithOrder(
                                cutoff,
                                PageRequest.of(
                                        0,
                                        batchSize
                                )
                        );

        for (CheckoutIdempotency candidate : candidates) {
            try {
                recoverOne(candidate.getId());
            } catch (Exception exception) {
                log.warn(
                        "Checkout idempotency recovery failed: id={}, message={}",
                        candidate.getId(),
                        exception.getMessage()
                );
            }
        }
    }

    @Transactional
    private void recoverOne(
            java.util.UUID idempotencyId
    ) {
        CheckoutIdempotency record =
                idempotencyRepository
                        .findByIdForUpdate(
                                idempotencyId
                        )
                        .orElse(null);

        if (record == null) {
            return;
        }

        if (record.getStatus() !=
                CheckoutIdempotencyStatus.PROCESSING) {
            return;
        }

        if (record.getOrderId() == null) {
            return;
        }

        Order order =
                orderRepository
                        .findByIdForUpdate(
                                record.getOrderId()
                        )
                        .orElse(null);

        if (order == null) {
            return;
        }

        Payment payment =
                paymentRepository
                        .findByOrderId(
                                order.getId()
                        )
                        .orElse(null);

        if (payment == null) {
            return;
        }

        boolean codCompleted =
                payment.getMethod() ==
                        PaymentMethod.CASH_ON_DELIVERY
                        && order.getStatus() ==
                        OrderStatus.CONFIRMED;

        boolean paystackCompleted =
                payment.getMethod() ==
                        PaymentMethod.PAYSTACK
                        && payment.getStatus() ==
                        PaymentStatus.SUCCESS
                        && order.getStatus() ==
                        OrderStatus.CONFIRMED;

        if (!codCompleted &&
                !paystackCompleted) {
            return;
        }

        CheckoutResponse response =
                buildRecoveredResponse(
                        order,
                        payment
                );

        idempotencyService.complete(
                record.getUserId(),
                record.getIdempotencyKey(),
                response
        );

        log.info(
                "Recovered stale checkout idempotency record: key={}, order={}",
                record.getIdempotencyKey(),
                order.getOrderNumber()
        );
    }

    private CheckoutResponse buildRecoveredResponse(
            Order order,
            Payment payment
    ) {
        List<SubOrder> subOrders =
                subOrderRepository
                        .findByOrderId(
                                order.getId()
                        );

        List<CheckoutResponse.SubOrderResponse>
                responseSubOrders =
                new ArrayList<>();

        for (SubOrder subOrder : subOrders) {
            List<OrderItem> orderItems =
                    orderItemRepository
                            .findBySubOrderId(
                                    subOrder.getId()
                            );

            List<CheckoutResponse.ItemResponse>
                    responseItems =
                    orderItems.stream()
                            .map(this::toItemResponse)
                            .toList();

            responseSubOrders.add(
                    new CheckoutResponse.SubOrderResponse(
                            subOrder.getVendor().getId(),
                            subOrder.getId(),
                            subOrder.getStatus()
                                    .name()
                                    .toLowerCase(),
                            responseItems
                    )
            );
        }

        String paymentMethod =
                payment.getMethod() ==
                        PaymentMethod.PAYSTACK
                        ? "paystack"
                        : "cod";

        return new CheckoutResponse(
                order.getId(),
                responseSubOrders,
                paymentMethod,
                null
        );
    }

    private CheckoutResponse.ItemResponse toItemResponse(
            OrderItem orderItem
    ) {
        return new CheckoutResponse.ItemResponse(
                orderItem.getProduct().getId(),
                orderItem.getProductName(),
                orderItem.getUnitPrice(),
                orderItem.getQuantity(),
                orderItem.getSubtotal()
        );
    }
}