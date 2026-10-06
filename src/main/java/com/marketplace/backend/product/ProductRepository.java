package com.marketplace.backend.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsByCategoryId(UUID categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM Product p
            WHERE p.id = :id
            """)
    Product findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT p
            FROM Product p
            JOIN p.category c
            WHERE p.status = com.marketplace.backend.product.ProductStatus.APPROVED
              AND (
                    :q = ''
                    OR LOWER(p.name) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', LOWER(:q), '%')
              )
              AND (
                    :category = ''
                    OR LOWER(c.name) = LOWER(:category)
              )
              AND (
                    :minPrice IS NULL
                    OR p.price >= :minPrice
              )
              AND (
                    :maxPrice IS NULL
                    OR p.price <= :maxPrice
              )
            """)
    Page<Product> searchApprovedProducts(
            @Param("q") String q,
            @Param("category") String category,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable
    );

    List<Product> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);
}