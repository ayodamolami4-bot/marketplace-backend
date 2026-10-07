package com.marketplace.backend.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public class CheckoutRequest {

    @NotNull
    private UUID addressId;

    @NotBlank
    @Pattern(regexp = "delivery|pickup", message = "Choose delivery or pickup")
    private String deliveryMethod;

    @NotBlank
    private String paymentMethod;

    @Pattern(regexp = "(?:[A-Za-z0-9][A-Za-z0-9_-]{1,49})?", message = "Invalid coupon code")
    private String couponCode;

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public UUID getAddressId() {
        return addressId;
    }

    public void setAddressId(UUID addressId) {
        this.addressId = addressId;
    }

    public String getDeliveryMethod() {
        return deliveryMethod;
    }

    public void setDeliveryMethod(String deliveryMethod) {
        this.deliveryMethod = deliveryMethod;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
