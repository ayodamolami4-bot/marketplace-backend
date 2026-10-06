package com.marketplace.backend.admin;

import jakarta.validation.constraints.NotBlank;

public record ReviewActionRequest(
        @NotBlank String action
) {
}