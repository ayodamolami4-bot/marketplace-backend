package com.marketplace.backend.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class CloudinaryService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final Cloudinary cloudinary;

    public CloudinaryService(org.springframework.beans.factory.ObjectProvider<Cloudinary> cloudinaryProvider) {
        this.cloudinary = cloudinaryProvider.getIfAvailable();
    }

    public CloudinaryUploadResponse uploadProductImage(
            MultipartFile file,
            UUID vendorId
    ) {
        validateImage(file);

        if (cloudinary == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudinary is not configured"
            );
        }

        try {
            String folder = "marketplace/products/" + vendorId;

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "use_filename", false,
                            "unique_filename", true,
                            "overwrite", false
                    )
            );

            String secureUrl = Objects.toString(
                    result.get("secure_url"),
                    null
            );

            String publicId = Objects.toString(
                    result.get("public_id"),
                    null
            );

            if (secureUrl == null || publicId == null) {
                throw new IllegalStateException(
                        "Cloudinary upload did not return required asset data"
                );
            }

            return new CloudinaryUploadResponse(
                    secureUrl,
                    publicId
            );

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary upload failed",
                    exception
            );
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image file is required"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image must not exceed 5 MB"
            );
        }

        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only image files are allowed"
            );
        }
    }
}