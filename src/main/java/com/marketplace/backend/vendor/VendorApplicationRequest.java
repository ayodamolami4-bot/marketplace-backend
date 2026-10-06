package com.marketplace.backend.vendor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VendorApplicationRequest {

    @NotBlank
    @Size(max = 150)
    private String businessName;

    @Size(max = 1000)
    private String businessDescription;

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getBusinessDescription() {
        return businessDescription;
    }

    public void setBusinessDescription(String businessDescription) {
        this.businessDescription = businessDescription;
    }
}