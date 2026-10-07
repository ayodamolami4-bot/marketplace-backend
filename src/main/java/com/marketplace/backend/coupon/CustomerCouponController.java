package com.marketplace.backend.coupon;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coupons")
public class CustomerCouponController {
    private final CouponDiscountService discounts;
    public CustomerCouponController(CouponDiscountService discounts) { this.discounts = discounts; }
    @PostMapping("/preview")
    public CouponDiscountService.Quote preview(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreviewRequest request) {
        return discounts.preview(UUID.fromString(jwt.getSubject()), request.code());
    }
    public record PreviewRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_-]{1,49}", message="Invalid coupon code") String code) {}
}
