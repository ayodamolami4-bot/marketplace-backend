package com.marketplace.backend.vendor;

import java.time.LocalDateTime;
import java.util.UUID;

public class VendorResponse {

    private UUID id;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String businessName;
    private String businessDescription;
    private VendorStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public VendorResponse(
            UUID id,
            UUID userId,
            String userName,
            String userEmail,
            String businessName,
            String businessDescription,
            VendorStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.businessName = businessName;
        this.businessDescription = businessDescription;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getBusinessDescription() {
        return businessDescription;
    }

    public VendorStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}