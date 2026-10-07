package com.marketplace.backend.auth;

import com.marketplace.backend.user.UserRepository;
import com.marketplace.backend.user.UserStatus;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;

public class ActiveAccountValidator implements OAuth2TokenValidator<Jwt> {
    private final UserRepository users;
    public ActiveAccountValidator(UserRepository users) { this.users = users; }
    @Override public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            boolean active = users.findById(UUID.fromString(token.getSubject()))
                    .map(user -> user.getStatus() == UserStatus.ACTIVE).orElse(false);
            if (active) return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException ignored) {
            // Malformed subject is invalid authentication, not a server failure.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Account is unavailable or inactive", null));
    }
}
