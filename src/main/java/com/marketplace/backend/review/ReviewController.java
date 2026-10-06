package com.marketplace.backend.review;

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
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<ApiListResponse<ProductReviewResponse>> getReviews(
            @PathVariable UUID productId
    ) {
        List<ProductReviewResponse> reviews =
                reviewService.getProductReviews(productId);

        return ResponseEntity.ok(
                new ApiListResponse<>(
                        reviews,
                        1,
                        reviews.size(),
                        reviews.size()
                )
        );
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        reviewService.createReview(
                                UUID.fromString(jwt.getSubject()),
                                productId,
                                request
                        )
                );
    }
}