package com.marketplace.backend.coupon;

import com.marketplace.backend.vendor.Vendor;
import com.marketplace.backend.vendor.VendorRepository;
import com.marketplace.backend.vendor.VendorStatus;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final VendorRepository vendorRepository;

    public CouponService(
            CouponRepository couponRepository,
            VendorRepository vendorRepository
    ) {
        this.couponRepository = couponRepository;
        this.vendorRepository = vendorRepository;
    }

    @Transactional
    public CouponResponse createCoupon(
            UUID userId,
            CouponRequest request
    ) {
        Vendor vendor = getApprovedVendor(userId);

        String code = normalizeCode(request.getCode());

        if (couponRepository.existsByVendorIdAndCode(
                vendor.getId(),
                code
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Coupon code already exists"
            );
        }

        Coupon coupon = new Coupon();
        coupon.setVendor(vendor);
        coupon.setCode(code);
        coupon.setDiscountPercent(request.getDiscountPercent());
        coupon.setExpiresAt(request.getExpiresAt());

        Coupon savedCoupon = couponRepository.save(coupon);

        return mapResponse(savedCoupon);
    }

    @Transactional
    public List<CouponResponse> getVendorCoupons(UUID userId) {
        Vendor vendor = getApprovedVendor(userId);

        return couponRepository
                .findByVendorIdOrderByCreatedAtDesc(vendor.getId())
                .stream()
                .map(this::mapResponse)
                .toList();
    }

    private Vendor getApprovedVendor(UUID userId) {
        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Vendor profile not found"
                        )
                );

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vendor account is not approved"
            );
        }

        return vendor;
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private CouponResponse mapResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDiscountPercent(),
                coupon.getExpiresAt()
        );
    }
}