package com.marketplace.backend.admin;

import com.marketplace.backend.review.Review;
import com.marketplace.backend.review.ReviewRepository;
import com.marketplace.backend.review.ReviewStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminReviewService {

    private final ReviewRepository reviewRepository;

    public AdminReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminReviewResponse> getReviews(ReviewStatus status) {
        List<Review> reviews = status == null
                ? reviewRepository.findAll()
                : reviewRepository.findByStatusOrderByCreatedAtDesc(status);

        return reviews.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminReviewResponse moderate(
            UUID reviewId,
            String action
    ) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Review not found"
                ));

        String normalizedAction = action.trim().toLowerCase(Locale.ROOT);

        switch (normalizedAction) {
            case "approve" -> review.setStatus(ReviewStatus.APPROVED);
            case "remove" -> review.setStatus(ReviewStatus.HIDDEN);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Action must be approve or remove"
            );
        }

        return toResponse(reviewRepository.save(review));
    }

    private AdminReviewResponse toResponse(Review review) {
        return new AdminReviewResponse(
                review.getId(),
                review.getUser().getId(),
                review.getUser().getName(),
                review.getProduct().getId(),
                review.getProduct().getName(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getRejectionReason(),
                review.getCreatedAt()
        );
    }
}