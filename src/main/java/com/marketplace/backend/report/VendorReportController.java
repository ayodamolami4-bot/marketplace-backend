package com.marketplace.backend.report;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendor/reports")
public class VendorReportController {

    private final VendorReportService vendorReportService;

    public VendorReportController(VendorReportService vendorReportService) {
        this.vendorReportService = vendorReportService;
    }

    @GetMapping
    public VendorReportResponse getReports(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return vendorReportService.getReports(userId);
    }
}