package com.marketplace.backend.coupon;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public class CouponRequest {

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,49}", message = "Coupon code must be 2 to 50 letters, digits, hyphens or underscores")
    private String code;

    @Min(1)
    @Max(100)
    private int discountPercent;

    @NotNull
    @Future
    private Instant expiresAt;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(int discountPercent) {
        this.discountPercent = discountPercent;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
