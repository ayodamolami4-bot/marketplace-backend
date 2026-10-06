package com.marketplace.backend.finance;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendor/finance")
public class VendorFinanceController {

    private final VendorFinanceService vendorFinanceService;

    public VendorFinanceController(VendorFinanceService vendorFinanceService) {
        this.vendorFinanceService = vendorFinanceService;
    }

    @GetMapping
    public VendorFinanceResponse getFinance(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return vendorFinanceService.getVendorFinance(userId);
    }
}