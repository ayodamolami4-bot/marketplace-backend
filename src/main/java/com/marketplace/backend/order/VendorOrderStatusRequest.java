package com.marketplace.backend.order;

import jakarta.validation.constraints.NotBlank;

public class VendorOrderStatusRequest {

    @NotBlank
    private String status;

    public VendorOrderStatusRequest() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}