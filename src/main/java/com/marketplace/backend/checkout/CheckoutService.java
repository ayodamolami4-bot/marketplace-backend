package com.marketplace.backend.checkout;

import com.marketplace.backend.cart.CartItem;
import com.marketplace.backend.cart.CartItemRepository;
import com.marketplace.backend.order.Order;
import com.marketplace.backend.order.OrderItem;
import com.marketplace.backend.order.OrderItemRepository;
import com.marketplace.backend.order.OrderRepository;
import com.marketplace.backend.order.OrderStatus;
import com.marketplace.backend.order.SubOrder;
import com.marketplace.backend.order.SubOrderRepository;
import com.marketplace.backend.payment.Payment;
import com.marketplace.backend.payment.PaymentMethod;
import com.marketplace.backend.payment.PaymentRepository;
import com.marketplace.backend.payment.PaymentService;
import com.marketplace.backend.payment.PaymentStatus;
import com.marketplace.backend.payment.paystackclient.PaystackClient;
import com.marketplace.backend.product.Product;
import com.marketplace.backend.product.ProductRepository;
import com.marketplace.backend.product.ProductStatus;
import com.marketplace.backend.user.Address;
import com.marketplace.backend.user.AddressRepository;
import com.marketplace.backend.user.User;
import com.marketplace.backend.user.UserRepository;
import com.marketplace.backend.vendor.Vendor;
import com.marketplace.backend.coupon.CouponDiscountService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CheckoutService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final CheckoutIdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;
    private final CouponDiscountService couponDiscountService;

    public CheckoutService(
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            AddressRepository addressRepository,
            UserRepository userRepository,
            OrderRepository orderRepository,
            SubOrderRepository subOrderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            CheckoutIdempotencyService idempotencyService,
            PlatformTransactionManager transactionManager,
            CouponDiscountService couponDiscountService
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.subOrderRepository = subOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
        this.couponDiscountService = couponDiscountService;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    public CheckoutResponse checkout(
            UUID userId,
            CheckoutRequest request,
            String idempotencyKey
    ) {
        PaymentMethod paymentMethod =
                parsePaymentMethod(
                        request.getPaymentMethod()
                );

        String normalizedIdempotencyKey =
                idempotencyService.normalizeKey(
                        idempotencyKey
                );

        String requestHash =
                idempotencyService.hashRequest(
                        userId,
                        request
                );

        if (paymentMethod == PaymentMethod.PAYSTACK) {
            paymentService.validatePaystackConfiguration();
        }

        CheckoutPreparation preparation =
                transactionTemplate.execute(status ->
                        prepareOrReplayCheckout(
                                userId,
                                request,
                                paymentMethod,
                                normalizedIdempotencyKey,
                                requestHash
                        )
                );

        if (preparation == null) {
            throw new IllegalStateException(
                    "Checkout transaction did not produce a result"
            );
        }

        if (preparation.replayResponse() != null) {
            return preparation.replayResponse();
        }

        PreparedCheckout prepared =
                preparation.prepared();

        CheckoutResponse.PaystackResponse paystackResponse =
                null;

        if (prepared.paystack()) {
            try {
                PaystackClient.InitializationResult initialized =
                        paymentService.initializePaystackPayment(
                                prepared.customerEmail(),
                                prepared.amount(),
                                prepared.orderNumber(),
                                prepared.orderId(),
                                prepared.paymentId(),
                                prepared.transactionReference()
                        );




                paystackResponse =
                        new CheckoutResponse.PaystackResponse(
                                initialized.authorizationUrl(),
                                initialized.reference()
                        );

            } catch (ResponseStatusException exception) {
                throw exception;

            } catch (Exception exception) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Unable to initialize Paystack payment"
                );
            }
        }

        CheckoutResponse response =
                new CheckoutResponse(
                        prepared.orderId(),
                        prepared.responseSubOrders(),
                        prepared.paymentMethod(),
                        paystackResponse
                );

        idempotencyService.complete(
                userId,
                normalizedIdempotencyKey,
                response
        );

        return response;
    }

    private CheckoutPreparation prepareOrReplayCheckout(
            UUID userId,
            CheckoutRequest request,
            PaymentMethod paymentMethod,
            String idempotencyKey,
            String requestHash
    ) {
        CheckoutIdempotencyService.Claim claim =
                idempotencyService.reserve(
                        userId,
                        idempotencyKey,
                        requestHash
                );

        if (!claim.newRequest()) {
            return new CheckoutPreparation(
                    null,
                    claim.replayResponse()
            );
        }

        PreparedCheckout prepared =
                prepareCheckout(
                        userId,
                        request,
                        paymentMethod
                );

        claim.record().setOrderId(
                prepared.orderId()
        );

        return new CheckoutPreparation(
                prepared,
                null
        );
    }

    private PreparedCheckout prepareCheckout(
            UUID userId,
            CheckoutRequest request,
            PaymentMethod paymentMethod
    ) {
        User user = getUser(userId);

        Address address =
                getOwnedAddress(
                        userId,
                        request.getAddressId()
                );

        List<CartItem> cartItems =
                cartItemRepository
                        .findByUserIdOrderByCreatedAtDesc(userId);

        if (cartItems.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cart is empty"
            );
        }

        Map<UUID, VendorCartGroup> groups =
                new LinkedHashMap<>();

        long subtotal = 0;

        for (CartItem cartItem : cartItems) {
            Product product =
                    productRepository.findByIdForUpdate(
                            cartItem.getProduct().getId()
                    );

            if (product == null ||
                    product.getStatus() != ProductStatus.APPROVED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A product in your cart is no longer available"
                );
            }

            if (product.getStockQuantity() <
                    cartItem.getQuantity()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient stock for " + product.getName()
                );
            }

            long itemSubtotal =
                    product.getPrice()
                            * cartItem.getQuantity();

            subtotal += itemSubtotal;

            UUID vendorId =
                    product.getVendor().getId();

            VendorCartGroup group =
                    groups.computeIfAbsent(
                            vendorId,
                            ignored ->
                                    new VendorCartGroup(
                                            product.getVendor()
                                    )
                    );

            group.items.add(
                    new PreparedItem(
                            product,
                            cartItem.getQuantity(),
                            itemSubtotal
                    )
            );

            product.setStockQuantity(
                    product.getStockQuantity()
                            - cartItem.getQuantity()
            );
        }

        long shippingFee = 0;
        Map<UUID, Long> vendorSubtotals = new LinkedHashMap<>();
        groups.forEach((vendorId, group) -> vendorSubtotals.put(vendorId, group.items.stream().mapToLong(PreparedItem::subtotal).sum()));
        Map<UUID, Long> vendorDiscounts = couponDiscountService.discounts(request.getCouponCode(), vendorSubtotals);
        long discountAmount = vendorDiscounts.values().stream().mapToLong(Long::longValue).sum();

        long totalAmount =
                subtotal
                        + shippingFee
                        - discountAmount;

        boolean paystack =
                paymentMethod == PaymentMethod.PAYSTACK && totalAmount > 0;

        Order order = new Order();

        order.setUser(user);
        order.setOrderNumber(
                generateOrderNumber()
        );

        order.setStatus(
                paystack
                        ? OrderStatus.PENDING_PAYMENT
                        : OrderStatus.CONFIRMED
        );

        order.setDeliveryMethod(
                request.getDeliveryMethod().trim()
        );

        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(totalAmount);

        order.setRecipientName(
                address.getRecipientName()
        );
        order.setPhoneNumber(
                address.getPhoneNumber()
        );
        order.setAddressLine(
                address.getAddressLine()
        );
        order.setCity(
                address.getCity()
        );
        order.setState(
                address.getState()
        );
        order.setCountry(
                address.getCountry()
        );
        order.setPostalCode(
                address.getPostalCode()
        );

        Order savedOrder =
                orderRepository.save(order);

        Payment payment = new Payment();

        payment.setOrder(savedOrder);
        payment.setMethod(paymentMethod);
        payment.setStatus(totalAmount == 0 ? PaymentStatus.SUCCESS : PaymentStatus.PENDING);
        payment.setAmount(totalAmount);

        if (paystack) {
            payment.setTransactionReference(
                    savedOrder.getOrderNumber()
            );
        }

        Payment savedPayment =
                paymentRepository.save(payment);

        List<CheckoutResponse.SubOrderResponse>
                responseSubOrders =
                new ArrayList<>();

        int vendorIndex = 1;

        for (VendorCartGroup group : groups.values()) {

            long vendorSubtotal =
                    group.items.stream()
                            .mapToLong(
                                    PreparedItem::subtotal
                            )
                            .sum();

            SubOrder subOrder = new SubOrder();

            subOrder.setOrder(savedOrder);
            subOrder.setVendor(group.vendor);

            subOrder.setSubOrderNumber(
                    savedOrder.getOrderNumber()
                            + "-V"
                            + vendorIndex
            );

            subOrder.setStatus(
                    paystack
                            ? OrderStatus.PENDING_PAYMENT
                            : OrderStatus.PENDING_FULFILLMENT
            );

            subOrder.setSubtotal(vendorSubtotal);
            subOrder.setShippingFee(0);
            long vendorDiscount = vendorDiscounts.getOrDefault(group.vendor.getId(), 0L);
            subOrder.setDiscountAmount(vendorDiscount);
            subOrder.setTotalAmount(vendorSubtotal - vendorDiscount);

            SubOrder savedSubOrder =
                    subOrderRepository.save(subOrder);

            List<CheckoutResponse.ItemResponse>
                    responseItems =
                    new ArrayList<>();

            for (PreparedItem prepared : group.items) {
                OrderItem orderItem = new OrderItem();

                orderItem.setSubOrder(
                        savedSubOrder
                );
                orderItem.setProduct(
                        prepared.product()
                );
                orderItem.setProductName(
                        prepared.product().getName()
                );
                orderItem.setUnitPrice(
                        prepared.product().getPrice()
                );
                orderItem.setQuantity(
                        prepared.quantity()
                );
                orderItem.setSubtotal(
                        prepared.subtotal()
                );

                orderItemRepository.save(orderItem);

                responseItems.add(
                        new CheckoutResponse.ItemResponse(
                                prepared.product().getId(),
                                prepared.product().getName(),
                                prepared.product().getPrice(),
                                prepared.quantity(),
                                prepared.subtotal()
                        )
                );
            }

            responseSubOrders.add(
                    new CheckoutResponse.SubOrderResponse(
                            group.vendor.getId(),
                            savedSubOrder.getId(),
                            savedSubOrder.getStatus()
                                    .name()
                                    .toLowerCase(),
                            responseItems
                    )
            );

            vendorIndex++;
        }

        cartItemRepository.deleteAll(cartItems);

        return new PreparedCheckout(
                savedOrder.getId(),
                savedOrder.getOrderNumber(),
                responseSubOrders,
                paymentMethod == PaymentMethod.PAYSTACK ? "paystack" : "cod",
                paystack,
                totalAmount,
                savedPayment.getId(),
                savedPayment.getTransactionReference(),
                user.getEmail()
        );
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found"
                        )
                );
    }

    private Address getOwnedAddress(
            UUID userId,
            UUID addressId
    ) {
        Address address =
                addressRepository.findById(addressId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Address not found"
                                )
                        );

        if (!address.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not own this address"
            );
        }

        return address;
    }

    private PaymentMethod parsePaymentMethod(
            String value
    ) {
        if (value == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment method is required"
            );
        }

        return switch (value.trim().toLowerCase()) {
            case "cod", "cash_on_delivery" ->
                    PaymentMethod.CASH_ON_DELIVERY;

            case "paystack" ->
                    PaymentMethod.PAYSTACK;

            default ->
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Unsupported payment method"
                    );
        };
    }

    private String generateOrderNumber() {
        return "ORD-"
                + Instant.now().toEpochMilli()
                + "-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    private static class VendorCartGroup {

        private final Vendor vendor;

        private final List<PreparedItem> items =
                new ArrayList<>();

        private VendorCartGroup(Vendor vendor) {
            this.vendor = vendor;
        }
    }

    private record PreparedItem(
            Product product,
            int quantity,
            long subtotal
    ) {
    }

    private record PreparedCheckout(
            UUID orderId,
            String orderNumber,
            List<CheckoutResponse.SubOrderResponse>
            responseSubOrders,
            String paymentMethod,
            boolean paystack,
            long amount,
            UUID paymentId,
            String transactionReference,
            String customerEmail
    ) {
    }

    private record CheckoutPreparation(
            PreparedCheckout prepared,
            CheckoutResponse replayResponse
    ) {
    }
}
