package com.marketplace.backend.cart;

import com.marketplace.backend.product.Product;
import com.marketplace.backend.product.ProductRepository;
import com.marketplace.backend.product.ProductStatus;
import com.marketplace.backend.user.User;
import com.marketplace.backend.user.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CartResponse getCart(UUID userId) {
        List<CartItem> items =
                cartItemRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return toResponse(items);
    }

    @Transactional
    public CartResponse addItem(
            UUID userId,
            CartItemRequest request
    ) {
        User user = getUser(userId);
        Product product = getPurchasableProduct(request.getProductId());

        if (request.getQuantity() > product.getStockQuantity()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Requested quantity exceeds available stock"
            );
        }

        CartItem cartItem =
                cartItemRepository
                        .findByUserIdAndProductId(
                                userId,
                                product.getId()
                        )
                        .orElse(null);

        if (cartItem == null) {
            cartItem = new CartItem();
            cartItem.setUser(user);
            cartItem.setProduct(product);
            cartItem.setQuantity(request.getQuantity());
        } else {
            long newQuantity =
                    (long) cartItem.getQuantity() + request.getQuantity();

            if (newQuantity > product.getStockQuantity()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Requested quantity exceeds available stock"
                );
            }

            cartItem.setQuantity((int) newQuantity);
        }

        cartItemRepository.save(cartItem);

        return getCart(userId);
    }

    @Transactional
    public CartResponse updateItem(
            UUID userId,
            UUID productId,
            int quantity
    ) {
        if (quantity < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be at least 1"
            );
        }

        CartItem cartItem =
                cartItemRepository
                        .findByUserIdAndProductId(userId, productId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Cart item not found"
                        ));

        Product product = getPurchasableProduct(productId);

        if (quantity > product.getStockQuantity()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Requested quantity exceeds available stock"
            );
        }

        cartItem.setQuantity(quantity);
        cartItemRepository.save(cartItem);

        return getCart(userId);
    }

    @Transactional
    public void removeItem(
            UUID userId,
            UUID productId
    ) {
        CartItem cartItem =
                cartItemRepository
                        .findByUserIdAndProductId(userId, productId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Cart item not found"
                        ));

        cartItemRepository.delete(cartItem);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }

    private Product getPurchasableProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        if (product.getStatus() != ProductStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product is not available for purchase"
            );
        }

        if (product.getStockQuantity() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product is out of stock"
            );
        }

        return product;
    }

    private CartResponse toResponse(List<CartItem> items) {
        long subtotal = 0;

        List<CartResponse.CartItemResponse> responseItems =
                items.stream()
                        .map(item -> {
                            Product product = item.getProduct();

                            return new CartResponse.CartItemResponse(
                                    product.getId(),
                                    item.getQuantity(),
                                    product.getPrice(),
                                    product.getVendor().getId()
                            );
                        })
                        .toList();

        for (CartResponse.CartItemResponse item : responseItems) {
            subtotal += item.price() * item.quantity();
        }

        return new CartResponse(
                responseItems,
                subtotal
        );
    }
}