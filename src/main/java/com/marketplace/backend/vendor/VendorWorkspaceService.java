package com.marketplace.backend.vendor;

import com.marketplace.backend.order.OrderStatus;
import com.marketplace.backend.order.SubOrder;
import com.marketplace.backend.order.SubOrderRepository;
import com.marketplace.backend.product.Product;
import com.marketplace.backend.product.ProductRepository;
import com.marketplace.backend.product.ProductStatus;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class VendorWorkspaceService {

    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Lagos");

    private final VendorRepository vendorRepository;
    private final ProductRepository productRepository;
    private final SubOrderRepository subOrderRepository;

    public VendorWorkspaceService(
            VendorRepository vendorRepository,
            ProductRepository productRepository,
            SubOrderRepository subOrderRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.productRepository = productRepository;
        this.subOrderRepository = subOrderRepository;
    }

    @Transactional
    public VendorDashboardResponse getDashboard(UUID userId) {
        Vendor vendor = getApprovedVendor(userId);

        List<Product> products =
                productRepository.findByVendorIdOrderByCreatedAtDesc(vendor.getId());

        List<SubOrder> orders =
                subOrderRepository.findByVendorIdOrderByCreatedAtDesc(vendor.getId());

        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        Instant startOfToday = today
                .atStartOfDay(BUSINESS_ZONE)
                .toInstant();

        Instant startOfTomorrow = today
                .plusDays(1)
                .atStartOfDay(BUSINESS_ZONE)
                .toInstant();

        List<SubOrder> todaysOrders = orders.stream()
                .filter(order -> order.getCreatedAt() != null)
                .filter(order ->
                        !order.getCreatedAt().isBefore(startOfToday)
                                && order.getCreatedAt().isBefore(startOfTomorrow)
                )
                .toList();

        long todayRevenue = todaysOrders.stream()
                .filter(order -> order.getStatus() != OrderStatus.PENDING_PAYMENT)
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .mapToLong(SubOrder::getTotalAmount)
                .sum();

        long lowStockCount = products.stream()
                .filter(product -> product.getStatus() == ProductStatus.APPROVED)
                .filter(product -> product.getStockQuantity() <= LOW_STOCK_THRESHOLD)
                .count();

        return new VendorDashboardResponse(
                vendor.getId(),
                vendor.getBusinessName(),
                products.size(),
                lowStockCount,
                todaysOrders.size(),
                todayRevenue
        );
    }

    @Transactional
    public VendorInventoryResponse getInventory(UUID userId) {
        Vendor vendor = getApprovedVendor(userId);

        List<VendorInventoryItemResponse> inventory =
                productRepository
                        .findByVendorIdOrderByCreatedAtDesc(vendor.getId())
                        .stream()
                        .map(this::mapInventoryItem)
                        .toList();

        return new VendorInventoryResponse(
                inventory,
                1,
                inventory.size(),
                inventory.size()
        );
    }

    @Transactional
    public VendorInventoryItemResponse updateInventory(
            UUID userId,
            UUID productId,
            VendorStockUpdateRequest request
    ) {
        Vendor vendor = getApprovedVendor(userId);

        Product product = productRepository.findByIdForUpdate(productId);

        if (product == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found"
            );
        }

        if (!product.getVendor().getId().equals(vendor.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have access to this product"
            );
        }

        product.setStockQuantity(request.stock());

        Product savedProduct = productRepository.save(product);

        return mapInventoryItem(savedProduct);
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

    private VendorInventoryItemResponse mapInventoryItem(Product product) {
        return new VendorInventoryItemResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getStatus()
        );
    }
}