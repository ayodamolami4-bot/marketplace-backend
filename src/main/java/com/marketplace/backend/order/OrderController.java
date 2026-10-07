package com.marketplace.backend.order;

import com.marketplace.backend.common.ApiListResponse;
import com.marketplace.backend.payment.PaymentRetryResponse;
import com.marketplace.backend.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderController(
            OrderService orderService,
            PaymentService paymentService
    ) {
        this.orderService =
                orderService;

        this.paymentService =
                paymentService;
    }

    @GetMapping("/orders")
    public ApiListResponse<OrderResponse> getOrders(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<OrderResponse> orders = orderService.getCustomerOrders(getUserId(jwt));
        return new ApiListResponse<>(orders, 1, orders.size(), orders.size());
    }

    @GetMapping("/orders/{id}")
    public OrderResponse getOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {

        return orderService
                .getCustomerOrder(
                        getUserId(jwt),
                        id
                );
    }

    @GetMapping("/vendor/orders")
    public ApiListResponse<OrderService.VendorOrderResponse>
    getVendorOrders(
            @AuthenticationPrincipal Jwt jwt
    ) {

        List<OrderService.VendorOrderResponse> orders =
                orderService.getVendorOrders(
                        getUserId(jwt)
                );

        return new ApiListResponse<>(
                orders,
                1,
                orders.size(),
                orders.size()
        );
    }

    @PatchMapping(
            "/vendor/orders/{id}/status"
    )
    public OrderService.VendorOrderResponse
    updateVendorOrderStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid
            @RequestBody
            VendorOrderStatusRequest request
    ) {

        return orderService
                .updateVendorOrderStatus(
                        getUserId(jwt),
                        id,
                        request
                );
    }

    @PostMapping(
            "/orders/{id}/payment/retry"
    )
    public PaymentRetryResponse retryPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {

        return paymentService
                .retryPaystackPayment(
                        id,
                        getUserId(jwt)
                );
    }

    private UUID getUserId(
            Jwt jwt
    ) {

        if (jwt == null ||
                jwt.getSubject() == null ||
                jwt.getSubject().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication"
            );
        }

        try {

            return UUID.fromString(
                    jwt.getSubject()
            );

        } catch (IllegalArgumentException exception) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication"
            );
        }
    }
}
