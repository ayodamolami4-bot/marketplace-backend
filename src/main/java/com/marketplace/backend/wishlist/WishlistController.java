package com.marketplace.backend.wishlist;

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
@RequestMapping("/api/v1/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public ResponseEntity<ApiListResponse<WishlistItemResponse>> getWishlist(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<WishlistItemResponse> items =
                wishlistService.getWishlist(getUserId(jwt));

        return ResponseEntity.ok(
                new ApiListResponse<>(
                        items,
                        1,
                        items.size(),
                        items.size()
                )
        );
    }

    @PostMapping
    public ResponseEntity<WishlistItemResponse> addItem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WishlistRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        wishlistService.addItem(
                                getUserId(jwt),
                                request.productId()
                        )
                );
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId
    ) {
        wishlistService.removeItem(
                getUserId(jwt),
                productId
        );

        return ResponseEntity.noContent().build();
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}