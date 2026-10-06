package com.marketplace.backend.vendor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/vendors")
public class AdminVendorController {

    private final VendorService vendorService;

    public AdminVendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<VendorResponse>> getPendingApplications() {
        return ResponseEntity.ok(
                vendorService.getPendingApplications()
        );
    }

    @PatchMapping("/{vendorId}/approve")
    public ResponseEntity<VendorResponse> approve(
            @PathVariable UUID vendorId,
            @Valid @RequestBody VendorApprovalRequest request
    ) {
        return ResponseEntity.ok(
                vendorService.approve(
                        vendorId,
                        request.getPaystackSubaccountCode()
                )
        );
    }

    @PatchMapping("/{vendorId}/reject")
    public ResponseEntity<VendorResponse> reject(
            @PathVariable UUID vendorId
    ) {
        return ResponseEntity.ok(
                vendorService.reject(vendorId)
        );
    }
}