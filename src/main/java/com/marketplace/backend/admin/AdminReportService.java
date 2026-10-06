package com.marketplace.backend.admin;

import com.marketplace.backend.order.OrderItem;
import com.marketplace.backend.order.OrderItemRepository;
import com.marketplace.backend.order.SubOrder;
import com.marketplace.backend.order.SubOrderRepository;
import com.marketplace.backend.payment.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminReportService {

    private final SubOrderRepository subOrderRepository;
    private final OrderItemRepository orderItemRepository;

    public AdminReportService(
            SubOrderRepository subOrderRepository,
            OrderItemRepository orderItemRepository
    ) {
        this.subOrderRepository = subOrderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public AdminReportResponse getReports() {
        List<SubOrder> paidSubOrders =
                subOrderRepository.findPaid(PaymentStatus.SUCCESS);

        if (paidSubOrders.isEmpty()) {
            return new AdminReportResponse(
                    0L,
                    0L,
                    List.of(),
                    List.of()
            );
        }

        long totalSales = paidSubOrders.stream()
                .mapToLong(SubOrder::getTotalAmount)
                .sum();

        Map<UUID, VendorAggregate> vendorAggregates = new HashMap<>();

        for (SubOrder subOrder : paidSubOrders) {
            UUID vendorId = subOrder.getVendor().getId();

            VendorAggregate aggregate =
                    vendorAggregates.computeIfAbsent(
                            vendorId,
                            ignored -> new VendorAggregate(
                                    vendorId,
                                    subOrder.getVendor().getBusinessName()
                            )
                    );

            aggregate.revenue += subOrder.getTotalAmount();
            aggregate.orders++;
        }

        List<UUID> subOrderIds = paidSubOrders.stream()
                .map(SubOrder::getId)
                .toList();

        List<OrderItem> items =
                orderItemRepository.findBySubOrderIdIn(subOrderIds);

        Map<UUID, CategoryAggregate> categoryAggregates = new HashMap<>();

        for (OrderItem item : items) {
            UUID categoryId = item.getProduct().getCategory().getId();
            String categoryName = item.getProduct().getCategory().getName();

            CategoryAggregate aggregate =
                    categoryAggregates.computeIfAbsent(
                            categoryId,
                            ignored -> new CategoryAggregate(
                                    categoryId,
                                    categoryName
                            )
                    );

            aggregate.revenue += item.getSubtotal();
        }

        List<AdminReportResponse.VendorPerformance> vendorPerformance =
                vendorAggregates.values().stream()
                        .sorted(
                                (a, b) ->
                                        Long.compare(
                                                b.revenue,
                                                a.revenue
                                        )
                        )
                        .map(aggregate ->
                                new AdminReportResponse.VendorPerformance(
                                        aggregate.vendorId,
                                        aggregate.vendorName,
                                        aggregate.revenue,
                                        aggregate.orders
                                )
                        )
                        .toList();

        List<AdminReportResponse.CategoryBreakdown> categoryBreakdown =
                categoryAggregates.values().stream()
                        .sorted(
                                (a, b) ->
                                        Long.compare(
                                                b.revenue,
                                                a.revenue
                                        )
                        )
                        .map(aggregate ->
                                new AdminReportResponse.CategoryBreakdown(
                                        aggregate.categoryId,
                                        aggregate.categoryName,
                                        aggregate.revenue
                                )
                        )
                        .toList();

        return new AdminReportResponse(
                totalSales,
                paidSubOrders.size(),
                vendorPerformance,
                categoryBreakdown
        );
    }

    private static class VendorAggregate {
        private final UUID vendorId;
        private final String vendorName;
        private long revenue;
        private long orders;

        private VendorAggregate(UUID vendorId, String vendorName) {
            this.vendorId = vendorId;
            this.vendorName = vendorName;
        }
    }

    private static class CategoryAggregate {
        private final UUID categoryId;
        private final String categoryName;
        private long revenue;

        private CategoryAggregate(UUID categoryId, String categoryName) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
        }
    }
}