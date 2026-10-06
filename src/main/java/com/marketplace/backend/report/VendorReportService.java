package com.marketplace.backend.report;

import com.marketplace.backend.order.OrderItem;
import com.marketplace.backend.order.OrderItemRepository;
import com.marketplace.backend.order.SubOrder;
import com.marketplace.backend.order.SubOrderRepository;
import com.marketplace.backend.payment.PaymentStatus;
import com.marketplace.backend.vendor.Vendor;
import com.marketplace.backend.vendor.VendorRepository;
import com.marketplace.backend.vendor.VendorStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VendorReportService {

    private final VendorRepository vendorRepository;
    private final SubOrderRepository subOrderRepository;
    private final OrderItemRepository orderItemRepository;

    public VendorReportService(
            VendorRepository vendorRepository,
            SubOrderRepository subOrderRepository,
            OrderItemRepository orderItemRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.subOrderRepository = subOrderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public VendorReportResponse getReports(UUID userId) {
        Vendor vendor = getApprovedVendor(userId);

        List<SubOrder> paidSubOrders =
                subOrderRepository.findPaidByVendorId(
                        vendor.getId(),
                        PaymentStatus.SUCCESS
                );

        if (paidSubOrders.isEmpty()) {
            return new VendorReportResponse(
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        List<UUID> subOrderIds = paidSubOrders.stream()
                .map(SubOrder::getId)
                .toList();

        List<OrderItem> items =
                orderItemRepository.findBySubOrderIdIn(subOrderIds);

        Map<UUID, Instant> subOrderDates = new HashMap<>();

        for (SubOrder subOrder : paidSubOrders) {
            subOrderDates.put(subOrder.getId(), subOrder.getCreatedAt());
        }

        Map<LocalDate, Long> revenueByDate = new HashMap<>();
        Map<UUID, ProductAggregate> productAggregates = new HashMap<>();
        Map<UUID, CategoryAggregate> categoryAggregates = new HashMap<>();

        for (OrderItem item : items) {
            Instant createdAt = subOrderDates.get(item.getSubOrder().getId());

            if (createdAt == null) {
                continue;
            }

            LocalDate date = createdAt.atZone(ZoneOffset.UTC).toLocalDate();

            revenueByDate.merge(
                    date,
                    item.getSubtotal(),
                    Long::sum
            );

            UUID productId = item.getProduct().getId();

            ProductAggregate productAggregate =
                    productAggregates.computeIfAbsent(
                            productId,
                            ignored -> new ProductAggregate(
                                    productId,
                                    item.getProductName()
                            )
                    );

            productAggregate.quantitySold += item.getQuantity();
            productAggregate.revenue += item.getSubtotal();

            UUID categoryId = item.getProduct().getCategory().getId();
            String categoryName = item.getProduct().getCategory().getName();

            CategoryAggregate categoryAggregate =
                    categoryAggregates.computeIfAbsent(
                            categoryId,
                            ignored -> new CategoryAggregate(
                                    categoryId,
                                    categoryName
                            )
                    );

            categoryAggregate.revenue += item.getSubtotal();
        }

        List<VendorReportResponse.RevenuePoint> revenueOverTime =
                revenueByDate.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry ->
                                new VendorReportResponse.RevenuePoint(
                                        entry.getKey(),
                                        entry.getValue()
                                )
                        )
                        .toList();

        List<VendorReportResponse.BestSeller> bestSellers =
                productAggregates.values().stream()
                        .sorted(
                                Comparator.comparingLong(
                                        ProductAggregate::getRevenue
                                ).reversed()
                        )
                        .map(product ->
                                new VendorReportResponse.BestSeller(
                                        product.productId,
                                        product.productName,
                                        product.quantitySold,
                                        product.revenue
                                )
                        )
                        .toList();

        List<VendorReportResponse.CategoryMix> categoryMix =
                categoryAggregates.values().stream()
                        .sorted(
                                Comparator.comparingLong(
                                        CategoryAggregate::getRevenue
                                ).reversed()
                        )
                        .map(category ->
                                new VendorReportResponse.CategoryMix(
                                        category.categoryId,
                                        category.categoryName,
                                        category.revenue
                                )
                        )
                        .toList();

        return new VendorReportResponse(
                revenueOverTime,
                bestSellers,
                categoryMix
        );
    }

    private Vendor getApprovedVendor(UUID userId) {
        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vendor profile not found"
                ));

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vendor account is not approved"
            );
        }

        return vendor;
    }

    private static class ProductAggregate {
        private final UUID productId;
        private final String productName;
        private int quantitySold;
        private long revenue;

        private ProductAggregate(UUID productId, String productName) {
            this.productId = productId;
            this.productName = productName;
        }

        private long getRevenue() {
            return revenue;
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

        private long getRevenue() {
            return revenue;
        }
    }
}