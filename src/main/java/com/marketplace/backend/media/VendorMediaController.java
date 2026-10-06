package com.marketplace.backend.media;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(
        "/api/v1/vendor/media"
)
public class VendorMediaController {

    private final CloudinaryImageService
            cloudinaryImageService;

    public VendorMediaController(
            CloudinaryImageService cloudinaryImageService
    ) {
        this.cloudinaryImageService =
                cloudinaryImageService;
    }

    @PostMapping(
            value = "/images",
            consumes = "multipart/form-data"
    )
    @ResponseStatus(
            HttpStatus.CREATED
    )
    public CloudinaryImageService.UploadResult uploadImage(
            @RequestPart("file")
            MultipartFile file
    ) {

        return cloudinaryImageService
                .uploadProductImage(
                        file
                );
    }
}