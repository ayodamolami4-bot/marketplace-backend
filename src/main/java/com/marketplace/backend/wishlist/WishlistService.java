package com.marketplace.backend.wishlist;

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
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public WishlistService(
            WishlistRepository wishlistRepository,
            WishlistItemRepository wishlistItemRepository,
            UserRepository userRepository,
            ProductRepository productRepository
    ) {
        this.wishlistRepository = wishlistRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getWishlist(UUID userId) {
        Wishlist wishlist = findWishlist(userId);

        return wishlistItemRepository
                .findByWishlistIdOrderByCreatedAtDesc(wishlist.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WishlistItemResponse addItem(
            UUID userId,
            UUID productId
    ) {
        Wishlist wishlist = getOrCreateWishlist(userId);

        if (wishlistItemRepository.existsByWishlistIdAndProductId(
                wishlist.getId(),
                productId
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product is already in wishlist"
            );
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        WishlistItem item = new WishlistItem();
        item.setWishlist(wishlist);
        item.setProduct(product);

        return toResponse(wishlistItemRepository.save(item));
    }

    @Transactional
    public void removeItem(
            UUID userId,
            UUID productId
    ) {
        Wishlist wishlist = findWishlist(userId);

        if (!wishlistItemRepository.existsByWishlistIdAndProductId(
                wishlist.getId(),
                productId
        )) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Wishlist item not found"
            );
        }

        wishlistItemRepository.deleteByWishlistIdAndProductId(
                wishlist.getId(),
                productId
        );
    }

    private Wishlist getOrCreateWishlist(UUID userId) {
        return wishlistRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "User not found"
                            ));

                    Wishlist wishlist = new Wishlist();
                    wishlist.setUser(user);

                    return wishlistRepository.save(wishlist);
                });
    }

    private Wishlist findWishlist(UUID userId) {
        return wishlistRepository.findByUserId(userId)
                .orElseGet(() -> getOrCreateWishlist(userId));
    }

    private WishlistItemResponse toResponse(WishlistItem item) {
        Product product = item.getProduct();

        return new WishlistItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getVendor().getBusinessName(),
                item.getCreatedAt()
        );
    }
}