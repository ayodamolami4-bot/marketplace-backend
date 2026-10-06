package com.marketplace.backend.cart;

import jakarta.validation.constraints.Min;

public class CartUpdateRequest {

    @Min(1)
    private int quantity;

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}