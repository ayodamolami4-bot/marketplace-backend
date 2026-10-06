package com.marketplace.backend.media;

public record CloudinaryUploadResponse(
        String url,
        String publicId
) {
}