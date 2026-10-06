package com.marketplace.backend.common;

public record ApiErrorResponse(
        ErrorDetail error
) {
    public record ErrorDetail(
            String code,
            String message
    ) {
    }
}