package com.marketplace.backend.vendor;

import jakarta.validation.constraints.NotBlank;

public class VendorApprovalRequest {

    @NotBlank
    private String paystackSubaccountCode;

    public String getPaystackSubaccountCode() {
        return paystackSubaccountCode;
    }

    public void setPaystackSubaccountCode(String paystackSubaccountCode) {
        this.paystackSubaccountCode = paystackSubaccountCode;
    }
}