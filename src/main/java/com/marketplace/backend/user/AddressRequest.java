package com.marketplace.backend.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.marketplace.backend.validation.ValidCountry;

public record AddressRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "(?=.*\\p{L})[\\p{L}\\p{M} .'-]+", message = "Recipient name must contain letters, spaces or name punctuation") String recipientName,
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "\\+?[0-9 ()-]{7,30}", message = "Phone number must use digits and optional +, spaces, parentheses or hyphens")
        @Pattern(regexp = "(?:[^0-9]*[0-9]){7,15}[^0-9]*", message = "Phone number must contain 7 to 15 digits") String phoneNumber,
        @NotBlank @Size(min = 5, max = 255)
        @Pattern(regexp = "(?s).*\\p{L}.*", message = "Address must contain a street or location name") String addressLine,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "(?=.*\\p{L})[\\p{L}\\p{M} .'-]+", message = "City must contain letters, spaces or place-name punctuation") String city,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "(?=.*\\p{L})[\\p{L}\\p{M} .'-]+", message = "State must contain letters, spaces or place-name punctuation") String state,
        @NotBlank @Size(max = 100) @ValidCountry String country,
        @Size(max = 20) String postalCode,
        boolean defaultAddress
) {
}
