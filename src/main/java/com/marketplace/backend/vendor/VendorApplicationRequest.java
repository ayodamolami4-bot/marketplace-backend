package com.marketplace.backend.vendor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public class VendorApplicationRequest {

    @NotBlank
    @Size(max = 150)
    @Pattern(regexp = "(?s).*\\p{L}.*", message = "Business name must contain letters")
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
