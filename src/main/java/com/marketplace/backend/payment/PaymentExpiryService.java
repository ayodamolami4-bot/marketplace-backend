package com.marketplace.backend.payment;

import com.marketplace.backend.common.DatabaseLockRetryExecutor;
import com.marketplace.backend.order.Order;
import com.marketplace.backend.order.OrderItem;
import com.marketplace.backend.order.OrderItemRepository;
import com.marketplace.backend.order.OrderRepository;
import com.marketplace.backend.order.OrderStatus;
import com.marketplace.backend.order.SubOrder;
import com.marketplace.backend.order.SubOrderRepository;
import com.marketplace.backend.product.Product;
import com.marketplace.backend.product.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentExpiryService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final DatabaseLockRetryExecutor lockRetryExecutor;

    public PaymentExpiryService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            OrderRepository orderRepository,
            SubOrderRepository subOrderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            DatabaseLockRetryExecutor lockRetryExecutor
    ) {
        this.paymentRepository =
                paymentRepository;

        this.paymentAttemptRepository =
                paymentAttemptRepository;

        this.orderRepository =
                orderRepository;

        this.subOrderRepository =
                subOrderRepository;

        this.orderItemRepository =
                orderItemRepository;

        this.productRepository =
                productRepository;

        this.lockRetryExecutor =
                lockRetryExecutor;
    }

    public int expireDuePayments(
            Instant cutoff,
            int batchSize
    ) {

        if (cutoff == null) {
            throw new IllegalArgumentException(
                    "Expiry cutoff is required"
            );
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "Expiry batch size must be positive"
            );
        }

        List<PaymentAttempt> candidates =
                paymentAttemptRepository
                        .findByStatusAndFailedAtLessThanEqualOrderByFailedAtAsc(
                                PaymentAttemptStatus.FAILED,
                                cutoff,
                                PageRequest.of(
                                        0,
                                        batchSize
                                )
                        );

        int expiredCount = 0;

        for (PaymentAttempt candidate :
                candidates) {

            String reference =
                    candidate.getReference();

            if (reference == null ||
                    reference.isBlank()) {
                continue;
            }

            Boolean expired =
                    lockRetryExecutor.execute(
                            "payment-expiry-" + reference,
                            () ->
                                    expireOne(
                                            reference,
                                            cutoff
                                    )
                    );

            if (Boolean.TRUE.equals(
                    expired
            )) {
                expiredCount++;
            }
        }

        return expiredCount;
    }

    private boolean expireOne(
            String reference,
            Instant cutoff
    ) {

        PaymentAttempt attempt =
                paymentAttemptRepository
                        .findByReferenceForUpdate(
                                reference
                        )
                        .orElse(null);

        if (attempt == null) {
            return false;
        }

        if (attempt.getStatus() !=
                PaymentAttemptStatus.FAILED) {

            return false;
        }

        if (attempt.getFailedAt() == null ||
                attempt.getFailedAt()
                        .isAfter(cutoff)) {

            return false;
        }

        Payment payment =
                paymentRepository
                        .findByIdForUpdate(
                                attempt.getPayment()
                                        .getId()
                        )
                        .orElse(null);

        if (payment == null) {
            return false;
        }

        if (payment.getMethod() !=
                PaymentMethod.PAYSTACK) {

            return false;
        }

        if (payment.getStatus() ==
                PaymentStatus.SUCCESS) {

            return false;
        }

        if (payment.getStatus() ==
                PaymentStatus.PENDING ||
                payment.getStatus() ==
                        PaymentStatus.PROCESSING) {

            return false;
        }

        if (payment.getStatus() !=
                PaymentStatus.FAILED) {

            return false;
        }

        if (!reference.equals(
                payment.getTransactionReference()
        )) {

            return false;
        }

        Order order =
                orderRepository
                        .findByIdForUpdate(
                                payment.getOrder()
                                        .getId()
                        )
                        .orElse(null);

        if (order == null) {
            return false;
        }

        if (order.getStatus() !=
                OrderStatus.PENDING_PAYMENT) {

            return false;
        }

        if (order.getStockReleasedAt()
                != null) {

            return false;
        }

        List<SubOrder> subOrders =
                subOrderRepository
                        .findByOrderId(
                                order.getId()
                        );

        List<UUID> subOrderIds =
                subOrders.stream()
                        .map(
                                SubOrder::getId
                        )
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
                        "Product not found during payment expiry: "
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
                        "Stock overflow during payment expiry for product "
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

        List<PaymentAttempt> attempts =
                paymentAttemptRepository
                        .findByPaymentIdOrderByAttemptNumberDesc(
                                payment.getId()
                        );

        for (PaymentAttempt paymentAttempt :
                attempts) {

            if (paymentAttempt.getStatus() ==
                    PaymentAttemptStatus.INITIATED ||
                    paymentAttempt.getStatus() ==
                            PaymentAttemptStatus.PENDING) {

                paymentAttempt.setStatus(
                        PaymentAttemptStatus.EXPIRED
                );

                paymentAttempt.setLastError(
                        "Payment window expired"
                );

                paymentAttemptRepository.save(
                        paymentAttempt
                );
            }
        }

        return true;
    }
}