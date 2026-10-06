package com.marketplace.backend.vendor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendor")
public class VendorWorkspaceController {

    private final VendorWorkspaceService vendorWorkspaceService;

    public VendorWorkspaceController(VendorWorkspaceService vendorWorkspaceService) {
        this.vendorWorkspaceService = vendorWorkspaceService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<VendorDashboardResponse> getDashboard(
            Authentication authentication
    ) {
        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                vendorWorkspaceService.getDashboard(userId)
        );
    }

    @GetMapping("/inventory")
    public ResponseEntity<VendorInventoryResponse> getInventory(
            Authentication authentication
    ) {
        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                vendorWorkspaceService.getInventory(userId)
        );
    }

    @PatchMapping("/inventory/{productId}")
    public ResponseEntity<VendorInventoryItemResponse> updateInventory(
            Authentication authentication,
            @PathVariable UUID productId,
            @Valid @RequestBody VendorStockUpdateRequest request
    ) {
        UUID userId = getUserId(authentication);

        return ResponseEntity.ok(
                vendorWorkspaceService.updateInventory(
                        userId,
                        productId,
                        request
                )
        );
    }

    private UUID getUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}