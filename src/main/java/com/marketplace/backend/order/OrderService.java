package com.marketplace.backend.order;

import com.marketplace.backend.user.UserRepository;
import com.marketplace.backend.vendor.Vendor;
import com.marketplace.backend.vendor.VendorRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;

    public OrderService(
            OrderRepository orderRepository,
            SubOrderRepository subOrderRepository,
            OrderItemRepository orderItemRepository,
            UserRepository userRepository,
            VendorRepository vendorRepository
    ) {
        this.orderRepository = orderRepository;
        this.subOrderRepository = subOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.vendorRepository = vendorRepository;
    }

    @Transactional
    public OrderResponse getCustomerOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));

        if (!order.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have access to this order"
            );
        }

        return toOrderResponse(order);
    }

    @Transactional
    public List<VendorOrderResponse> getVendorOrders(UUID userId) {
        Vendor vendor = getVendor(userId);

        List<SubOrder> subOrders =
                subOrderRepository.findByVendorIdOrderByCreatedAtDesc(vendor.getId());

        return subOrders.stream()
                .map(this::toVendorOrderResponse)
                .toList();
    }

    @Transactional
    public VendorOrderResponse updateVendorOrderStatus(
            UUID userId,
            UUID subOrderId,
            VendorOrderStatusRequest request
    ) {
        Vendor vendor = getVendor(userId);

        SubOrder subOrder = subOrderRepository.findById(subOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));

        if (!subOrder.getVendor().getId().equals(vendor.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have access to this order"
            );
        }

        OrderStatus newStatus = parseVendorStatus(request.getStatus());

        validateStatusTransition(subOrder.getStatus(), newStatus);

        subOrder.setStatus(newStatus);

        SubOrder savedSubOrder = subOrderRepository.save(subOrder);

        updateParentOrderStatus(subOrder.getOrder());

        return toVendorOrderResponse(savedSubOrder);
    }

    private Vendor getVendor(UUID userId) {
        return vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Vendor account not found"
                ));
    }

    private OrderResponse toOrderResponse(Order order) {
        List<SubOrder> subOrders =
                subOrderRepository.findByOrderId(order.getId());

        List<OrderResponse.SubOrderResponse> responseSubOrders =
                new ArrayList<>();

        for (SubOrder subOrder : subOrders) {
            List<OrderItem> items =
                    orderItemRepository.findBySubOrderId(subOrder.getId());

            List<OrderResponse.ItemResponse> responseItems =
                    items.stream()
                            .map(item -> new OrderResponse.ItemResponse(
                                    item.getProduct().getId(),
                                    item.getProductName(),
                                    item.getUnitPrice(),
                                    item.getQuantity(),
                                    item.getSubtotal()
                            ))
                            .toList();

            responseSubOrders.add(
                    new OrderResponse.SubOrderResponse(
                            subOrder.getVendor().getId(),
                            subOrder.getVendor().getBusinessName(),
                            subOrder.getStatus().name().toLowerCase(),
                            responseItems,
                            null
                    )
            );
        }

        return new OrderResponse(
                order.getId(),
                order.getStatus().name().toLowerCase(),
                order.getCreatedAt(),
                responseSubOrders
        );
    }

    private VendorOrderResponse toVendorOrderResponse(SubOrder subOrder) {
        List<OrderItem> items =
                orderItemRepository.findBySubOrderId(subOrder.getId());

        List<VendorOrderResponse.ItemResponse> responseItems =
                items.stream()
                        .map(item -> new VendorOrderResponse.ItemResponse(
                                item.getProduct().getId(),
                                item.getProductName(),
                                item.getUnitPrice(),
                                item.getQuantity(),
                                item.getSubtotal()
                        ))
                        .toList();

        return new VendorOrderResponse(
                subOrder.getId(),
                subOrder.getOrder().getId(),
                subOrder.getVendor().getId(),
                subOrder.getVendor().getBusinessName(),
                subOrder.getStatus().name().toLowerCase(),
                responseItems
        );
    }

    private OrderStatus parseVendorStatus(String value) {
        if (value == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status is required"
            );
        }

        return switch (value.trim().toLowerCase()) {
            case "processing" -> OrderStatus.PROCESSING;
            case "shipped" -> OrderStatus.SHIPPED;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vendor can only set status to processing or shipped"
            );
        };
    }

    private void validateStatusTransition(
            OrderStatus currentStatus,
            OrderStatus newStatus
    ) {
        boolean valid =
                (currentStatus == OrderStatus.PENDING_FULFILLMENT
                        && newStatus == OrderStatus.PROCESSING)
                        ||
                        (currentStatus == OrderStatus.PROCESSING
                                && newStatus == OrderStatus.SHIPPED);

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid order status transition"
            );
        }
    }

    private void updateParentOrderStatus(Order order) {
        List<SubOrder> subOrders =
                subOrderRepository.findByOrderId(order.getId());

        if (subOrders.isEmpty()) {
            return;
        }

        boolean allDelivered = subOrders.stream()
                .allMatch(subOrder ->
                        subOrder.getStatus() == OrderStatus.DELIVERED);

        if (allDelivered) {
            order.setStatus(OrderStatus.DELIVERED);
            orderRepository.save(order);
            return;
        }

        boolean allShippedOrDelivered = subOrders.stream()
                .allMatch(subOrder ->
                        subOrder.getStatus() == OrderStatus.SHIPPED
                                || subOrder.getStatus() == OrderStatus.DELIVERED);

        if (allShippedOrDelivered) {
            order.setStatus(OrderStatus.SHIPPED);
            orderRepository.save(order);
            return;
        }

        boolean anyProcessingOrShipped = subOrders.stream()
                .anyMatch(subOrder ->
                        subOrder.getStatus() == OrderStatus.PROCESSING
                                || subOrder.getStatus() == OrderStatus.SHIPPED);

        if (anyProcessingOrShipped) {
            order.setStatus(OrderStatus.PROCESSING);
            orderRepository.save(order);
        }
    }

    public record VendorOrderResponse(
            UUID id,
            UUID orderId,
            UUID vendorId,
            String vendorName,
            String status,
            List<ItemResponse> items
    ) {
        public record ItemResponse(
                UUID productId,
                String productName,
                long unitPrice,
                int quantity,
                long subtotal
        ) {
        }
    }
}