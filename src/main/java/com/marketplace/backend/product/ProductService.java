package com.marketplace.backend.product;

import com.marketplace.backend.category.Category;
import com.marketplace.backend.category.CategoryRepository;
import com.marketplace.backend.common.ApiListResponse;
import com.marketplace.backend.review.Review;
import com.marketplace.backend.review.ReviewRepository;
import com.marketplace.backend.review.ReviewStatus;
import com.marketplace.backend.vendor.Vendor;
import com.marketplace.backend.vendor.VendorRepository;
import com.marketplace.backend.vendor.VendorStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProductService {

    private static final Pattern CLOUDINARY_VERSION =
            Pattern.compile("^v\\d+$");

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final VendorRepository vendorRepository;
    private final ReviewRepository reviewRepository;

    public ProductService(
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            CategoryRepository categoryRepository,
            VendorRepository vendorRepository,
            ReviewRepository reviewRepository
    ) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.vendorRepository = vendorRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public ProductResponse create(
            UUID userId,
            ProductRequest request
    ) {
        Vendor vendor = getApprovedVendor(userId);
        Category category = getCategory(request.getCategoryId());

        validateImages(request.getImages());

        Product product = new Product();
        product.setVendor(vendor);
        product.setCategory(category);
        product.setName(request.getName().trim());
        product.setDescription(normalize(request.getDescription()));
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStock());
        product.setStatus(ProductStatus.APPROVED);

        Product savedProduct = productRepository.save(product);

        saveImages(savedProduct, request.getImages());

        return toResponse(savedProduct);
    }

    @Transactional
    public ProductResponse update(
            UUID userId,
            UUID productId,
            ProductRequest request
    ) {
        Vendor vendor = getApprovedVendor(userId);
        Product product = getOwnedProduct(productId, vendor.getId());
        Category category = getCategory(request.getCategoryId());

        validateImages(request.getImages());

        product.setCategory(category);
        product.setName(request.getName().trim());
        product.setDescription(normalize(request.getDescription()));
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStock());

        Product savedProduct = productRepository.save(product);

        replaceImages(savedProduct, request.getImages());

        return toResponse(savedProduct);
    }

    @Transactional
    public void delete(UUID userId, UUID productId) {
        Vendor vendor = getApprovedVendor(userId);
        Product product = getOwnedProduct(productId, vendor.getId());

        productImageRepository.deleteAll(
                productImageRepository
                        .findByProductIdOrderByDisplayOrderAsc(product.getId())
        );

        productRepository.delete(product);
    }

    @Transactional
    public ProductResponse getPublicProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        if (product.getStatus() != ProductStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found"
            );
        }

        return toResponse(product);
    }
    @Transactional
    public ApiListResponse<ProductListResponse> getPublicProducts(
            String q,
            String category,
            Long minPrice,
            Long maxPrice,
            Pageable pageable
    ) {
        if (minPrice != null && minPrice < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "minPrice cannot be negative"
            );
        }

        if (maxPrice != null && maxPrice < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "maxPrice cannot be negative"
            );
        }

        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "minPrice cannot exceed maxPrice"
            );
        }

        Page<Product> products = productRepository.searchApprovedProducts(
                q == null ? "" : q.trim(),
                category == null ? "" : category.trim(),
                minPrice,
                maxPrice,
                pageable
        );

        List<ProductListResponse> data = products.getContent()
                .stream()
                .map(this::toListResponse)
                .toList();

        return new ApiListResponse<>(
                data,
                products.getNumber() + 1,
                products.getSize(),
                products.getTotalElements()
        );
    }

    public Sort resolveSort(String sort) {
        String normalized = sort == null
                ? "newest"
                : sort.trim().toLowerCase();

        return switch (normalized) {
            case "price_asc" ->
                    Sort.by(Sort.Direction.ASC, "price");

            case "price_desc" ->
                    Sort.by(Sort.Direction.DESC, "price");

            case "oldest" ->
                    Sort.by(Sort.Direction.ASC, "createdAt");

            case "newest" ->
                    Sort.by(Sort.Direction.DESC, "createdAt");

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid sort value"
            );
        };
    }

    private Vendor getApprovedVendor(UUID userId) {
        Vendor vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vendor profile not found"
                ));

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only approved vendors can manage products"
            );
        }

        return vendor;
    }

    private Product getOwnedProduct(
            UUID productId,
            UUID vendorId
    ) {
        Product product = productRepository.findByIdForUpdate(productId);

        if (product == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found"
            );
        }

        if (!product.getVendor().getId().equals(vendorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not own this product"
            );
        }

        return product;
    }
    private Category getCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Category not found"
                ));
    }

    private void validateImages(List<String> images) {
        if (images == null || images.size() > 8) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A product can have at most 8 images"
            );
        }

        for (String image : images) {
            if (image == null || image.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Image URL cannot be blank"
                );
            }

            if (!image.startsWith("https://")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product images must use HTTPS URLs"
                );
            }

            if (!isCloudinaryUrl(image)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product images must be Cloudinary URLs"
                );
            }
        }
    }

    private boolean isCloudinaryUrl(String url) {
        return url.contains("res.cloudinary.com/")
                && url.contains("/image/upload/");
    }

    private void saveImages(
            Product product,
            List<String> imageUrls
    ) {
        List<ProductImage> images = new ArrayList<>();

        for (int index = 0; index < imageUrls.size(); index++) {
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl(imageUrls.get(index));
            image.setPublicId(extractPublicId(imageUrls.get(index)));
            image.setDisplayOrder(index);

            images.add(image);
        }

        if (!images.isEmpty()) {
            productImageRepository.saveAll(images);
        }
    }

    private void replaceImages(
            Product product,
            List<String> imageUrls
    ) {
        productImageRepository.deleteAll(
                productImageRepository
                        .findByProductIdOrderByDisplayOrderAsc(
                                product.getId()
                        )
        );

        saveImages(product, imageUrls);
    }

    private ProductResponse toResponse(Product product) {
        List<ProductImage> images =
                productImageRepository
                        .findByProductIdOrderByDisplayOrderAsc(
                                product.getId()
                        );

        List<Review> reviews =
                reviewRepository
                        .findByProductIdAndStatusOrderByCreatedAtDesc(
                                product.getId(),
                                ReviewStatus.APPROVED
                        );

        double productAverage = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        double vendorAverage =
                calculateVendorAverage(product.getVendor().getId());

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                images.stream()
                        .map(ProductImage::getImageUrl)
                        .toList(),
                product.getStockQuantity(),
                new ProductResponse.VendorSummary(
                        product.getVendor().getId(),
                        product.getVendor().getBusinessName(),
                        vendorAverage
                ),
                product.getCategory().getName(),
                new ProductResponse.ReviewSummary(
                        productAverage,
                        reviews.size()
                )
        );
    }

    private ProductListResponse toListResponse(Product product) {
        List<ProductImage> images =
                productImageRepository
                        .findByProductIdOrderByDisplayOrderAsc(
                                product.getId()
                        );

        List<Review> reviews =
                reviewRepository
                        .findByProductIdAndStatusOrderByCreatedAtDesc(
                                product.getId(),
                                ReviewStatus.APPROVED
                        );

        double average = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        String thumbnail = images.isEmpty()
                ? null
                : images.get(0).getImageUrl();

        return new ProductListResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                thumbnail,
                new ProductListResponse.VendorSummary(
                        product.getVendor().getId(),
                        product.getVendor().getBusinessName()
                ),
                average,
                product.getCategory().getName()
        );
    }

    private double calculateVendorAverage(UUID vendorId) {
        List<Product> vendorProducts = productRepository.findAll()
                .stream()
                .filter(product ->
                        product.getVendor().getId().equals(vendorId))
                .toList();

        List<Review> reviews = new ArrayList<>();

        for (Product product : vendorProducts) {
            reviews.addAll(
                    reviewRepository
                            .findByProductIdAndStatusOrderByCreatedAtDesc(
                                    product.getId(),
                                    ReviewStatus.APPROVED
                            )
            );
        }

        return reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
    }

    private String extractPublicId(String url) {
        int uploadIndex = url.indexOf("/image/upload/");

        if (uploadIndex < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid Cloudinary image URL"
            );
        }

        String path = url.substring(
                uploadIndex + "/image/upload/".length()
        );

        String[] segments = path.split("/");

        int startIndex = 0;

        for (int index = 0; index < segments.length; index++) {
            if (CLOUDINARY_VERSION.matcher(segments[index]).matches()) {
                startIndex = index + 1;
                break;
            }
        }

        if (startIndex >= segments.length) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Could not determine Cloudinary public ID"
            );
        }

        StringBuilder publicId = new StringBuilder();

        for (int index = startIndex; index < segments.length; index++) {
            if (publicId.length() > 0) {
                publicId.append('/');
            }

            publicId.append(segments[index]);
        }

        int extensionIndex = publicId.lastIndexOf(".");

        if (extensionIndex > 0) {
            publicId.delete(extensionIndex, publicId.length());
        }

        return publicId.toString();
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}