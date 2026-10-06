package com.marketplace.backend.user;

import com.marketplace.backend.common.ApiListResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<ApiListResponse<AddressResponse>> getAddresses(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<AddressResponse> addresses =
                addressService.getUserAddresses(getUserId(jwt));

        return ResponseEntity.ok(
                new ApiListResponse<>(
                        addresses,
                        1,
                        addresses.size(),
                        addresses.size()
                )
        );
    }

    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        addressService.createAddress(
                                getUserId(jwt),
                                request
                        )
                );
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request
    ) {
        return ResponseEntity.ok(
                addressService.updateAddress(
                        getUserId(jwt),
                        addressId,
                        request
                )
        );
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId
    ) {
        addressService.deleteAddress(
                getUserId(jwt),
                addressId
        );

        return ResponseEntity.noContent().build();
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}