package com.marketplace.backend.wishlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    List<WishlistItem> findByWishlistIdOrderByCreatedAtDesc(UUID wishlistId);

    boolean existsByWishlistIdAndProductId(UUID wishlistId, UUID productId);

    void deleteByWishlistIdAndProductId(UUID wishlistId, UUID productId);
}