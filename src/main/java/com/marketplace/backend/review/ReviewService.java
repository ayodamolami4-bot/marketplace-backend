package com.marketplace.backend.review;

import com.marketplace.backend.product.Product;
import com.marketplace.backend.product.ProductRepository;
import com.marketplace.backend.user.User;
import com.marketplace.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            UserRepository userRepository,
            ProductRepository productRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductReviewResponse> getProductReviews(UUID productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        return reviewRepository
                .findByProductIdAndStatusOrderByCreatedAtDesc(
                        productId,
                        ReviewStatus.APPROVED
                )
                .stream()
                .map(review -> new ProductReviewResponse(
                        review.getId(),
                        review.getUser().getId(),
                        review.getUser().getName(),
                        review.getRating(),
                        review.getComment(),
                        review.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public ReviewResponse createReview(
            UUID userId,
            UUID productId,
            ReviewRequest request
    ) {
        if (reviewRepository.existsByUserIdAndProductId(
                userId,
                productId
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You have already reviewed this product"
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(request.rating());
        review.setComment(
                request.comment() == null || request.comment().isBlank()
                        ? null
                        : request.comment().trim()
        );
        review.setStatus(ReviewStatus.PENDING);

        Review saved = reviewRepository.save(review);

        return new ReviewResponse(
                saved.getId(),
                productId,
                saved.getRating(),
                saved.getComment(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }
}