package com.marketplace.backend.coupon;

import com.marketplace.backend.common.ApiListResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendor/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public ApiListResponse<CouponResponse> getCoupons(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);

        List<CouponResponse> coupons = couponService.getVendorCoupons(userId);

        return new ApiListResponse<>(
                coupons,
                1,
                coupons.size(),
                coupons.size()
        );
    }

    @PostMapping
    public CouponResponse createCoupon(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CouponRequest request
    ) {
        UUID userId = getUserId(jwt);

        return couponService.createCoupon(userId, request);
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}