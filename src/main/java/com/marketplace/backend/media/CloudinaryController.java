package com.marketplace.backend.media;

import com.marketplace.backend.vendor.Vendor;
import com.marketplace.backend.vendor.VendorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendor/uploads")
public class CloudinaryController {

    private final CloudinaryService cloudinaryService;
    private final VendorRepository vendorRepository;

    public CloudinaryController(
            CloudinaryService cloudinaryService,
            VendorRepository vendorRepository
    ) {
        this.cloudinaryService = cloudinaryService;
        this.vendorRepository = vendorRepository;
    }

    @PostMapping("/images")
    public ResponseEntity<CloudinaryUploadResponse> uploadProductImage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vendor profile not found"
                ));

        if (vendor.getStatus() != com.marketplace.backend.vendor.VendorStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only approved vendors can upload product images"
            );
        }

        return ResponseEntity.ok(
                cloudinaryService.uploadProductImage(file, vendor.getId())
        );
    }
}