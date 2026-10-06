package com.marketplace.backend.auth;

import java.util.List;
import java.util.UUID;

public class AuthResponse {

    private UserResponse user;
    private String token;

    public AuthResponse(UserResponse user, String token) {
        this.user = user;
        this.token = token;
    }

    public UserResponse getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }

    public static class UserResponse {

        private UUID id;
        private String email;
        private String name;
        private List<String> roles;

        public UserResponse(
                UUID id,
                String email,
                String name,
                List<String> roles
        ) {
            this.id = id;
            this.email = email;
            this.name = name;
            this.roles = roles;
        }

        public UUID getId() {
            return id;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }

        public List<String> getRoles() {
            return roles;
        }
    }
}