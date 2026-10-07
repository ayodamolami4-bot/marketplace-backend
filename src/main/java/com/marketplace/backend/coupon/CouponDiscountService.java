package com.marketplace.backend.coupon;

import com.marketplace.backend.cart.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class CouponDiscountService {
    private final CouponRepository coupons;
    private final CartItemRepository cart;
    public CouponDiscountService(CouponRepository coupons, CartItemRepository cart) { this.coupons = coupons; this.cart = cart; }
    @Transactional(readOnly = true)
    public Map<UUID, Long> discounts(String code, Map<UUID, Long> subtotals) {
        Map<UUID, Long> result = new HashMap<>();
        if (code == null || code.isBlank()) return result;
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (var entry : subtotals.entrySet()) {
            coupons.findByVendorIdAndCode(entry.getKey(), normalized)
                .filter(coupon -> coupon.getExpiresAt().isAfter(Instant.now()))
                .ifPresent(coupon -> result.put(entry.getKey(), entry.getValue() * coupon.getDiscountPercent() / 100));
        }
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon is invalid, expired, or does not apply to sellers in your cart");
        return result;
    }
    @Transactional(readOnly = true)
    public Quote preview(UUID userId, String code) {
        Map<UUID, Long> subtotals = new HashMap<>();
        for (var item : cart.findByUserIdOrderByCreatedAtDesc(userId)) {
            subtotals.merge(item.getProduct().getVendor().getId(), item.getProduct().getPrice() * item.getQuantity(), Long::sum);
        }
        if (subtotals.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty");
        long subtotal = subtotals.values().stream().mapToLong(Long::longValue).sum();
        long discount = discounts(code, subtotals).values().stream().mapToLong(Long::longValue).sum();
        return new Quote(subtotal, discount, subtotal - discount);
    }
    public record Quote(long subtotal, long discountAmount, long totalAmount) {}
}
