package com.marketplace.backend.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class CloudinaryImageService {

    private static final long MAX_FILE_SIZE =
            10L * 1024L * 1024L;

    private final Cloudinary cloudinary;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;

    public CloudinaryImageService(
            Cloudinary cloudinary,
            @Value("${cloudinary.cloud-name:}")
            String cloudName,
            @Value("${cloudinary.api-key:}")
            String apiKey,
            @Value("${cloudinary.api-secret:}")
            String apiSecret
    ) {
        this.cloudinary = cloudinary;
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public UploadResult uploadProductImage(
            MultipartFile file
    ) {

        validateConfiguration();
        validateFile(file);

        try {

            Map<?, ?> result =
                    cloudinary.uploader()
                            .upload(
                                    file.getBytes(),
                                    ObjectUtils.asMap(
                                            "folder",
                                            "marketplace/products",
                                            "resource_type",
                                            "image",
                                            "use_filename",
                                            true,
                                            "unique_filename",
                                            true,
                                            "overwrite",
                                            false
                                    )
                            );

            String secureUrl =
                    value(
                            result,
                            "secure_url"
                    );

            String publicId =
                    value(
                            result,
                            "public_id"
                    );

            String format =
                    value(
                            result,
                            "format"
                    );

            int width =
                    intValue(
                            result,
                            "width"
                    );

            int height =
                    intValue(
                            result,
                            "height"
                    );

            long bytes =
                    longValue(
                            result,
                            "bytes"
                    );

            if (secureUrl == null ||
                    publicId == null) {

                throw new IllegalStateException(
                        "Cloudinary returned an incomplete upload response"
                );
            }

            return new UploadResult(
                    publicId,
                    secureUrl,
                    format,
                    width,
                    height,
                    bytes
            );

        } catch (ResponseStatusException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Image upload provider is currently unavailable"
            );
        }
    }

    private void validateConfiguration() {

        if (cloudName == null ||
                cloudName.isBlank() ||
                apiKey == null ||
                apiKey.isBlank() ||
                apiSecret == null ||
                apiSecret.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudinary is not configured"
            );
        }
    }

    private void validateFile(
            MultipartFile file
    ) {

        if (file == null ||
                file.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image file is required"
            );
        }

        if (file.getSize() >
                MAX_FILE_SIZE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image must not exceed 10 MB"
            );
        }

        String contentType =
                file.getContentType();

        if (contentType == null ||
                !isAllowedImageType(
                        contentType
                )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, WebP and GIF images are allowed"
            );
        }
    }

    private boolean isAllowedImageType(
            String contentType
    ) {

        return switch (
                contentType.toLowerCase()
                ) {

            case "image/jpeg",
                 "image/png",
                 "image/webp",
                 "image/gif" ->
                    true;

            default ->
                    false;
        };
    }

    private String value(
            Map<?, ?> result,
            String key
    ) {

        Object value =
                result.get(
                        key
                );

        return value == null
                ? null
                : value.toString();
    }

    private int intValue(
            Map<?, ?> result,
            String key
    ) {

        Object value =
                result.get(
                        key
                );

        if (value instanceof Number number) {
            return number.intValue();
        }

        return 0;
    }

    private long longValue(
            Map<?, ?> result,
            String key
    ) {

        Object value =
                result.get(
                        key
                );

        if (value instanceof Number number) {
            return number.longValue();
        }

        return 0L;
    }

    public record UploadResult(
            String publicId,
            String secureUrl,
            String format,
            int width,
            int height,
            long bytes
    ) {
    }
}