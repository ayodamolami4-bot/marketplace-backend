package com.marketplace.backend.admin;

import com.marketplace.backend.common.ApiListResponse;
import com.marketplace.backend.review.ReviewStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    public AdminReviewController(AdminReviewService adminReviewService) {
        this.adminReviewService = adminReviewService;
    }

    @GetMapping
    public ResponseEntity<ApiListResponse<AdminReviewResponse>> getReviews(
            @RequestParam(required = false) ReviewStatus status
    ) {
        List<AdminReviewResponse> reviews =
                adminReviewService.getReviews(status);

        return ResponseEntity.ok(
                new ApiListResponse<>(
                        reviews,
                        1,
                        reviews.size(),
                        reviews.size()
                )
        );
    }

    @PatchMapping("/{reviewId}")
    public ResponseEntity<AdminReviewResponse> moderateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewActionRequest request
    ) {
        return ResponseEntity.ok(
                adminReviewService.moderate(
                        reviewId,
                        request.action()
                )
        );
    }
}