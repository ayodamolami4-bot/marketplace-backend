package com.marketplace.backend.auth;

import java.util.List;
import java.util.UUID;

public class LoginResponse {

    private UUID userId;
    private String email;
    private String name;
    private List<String> roles;

    public LoginResponse(
            UUID userId,
            String email,
            String name,
            List<String> roles
    ) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.roles = roles;
    }

    public UUID getUserId() {
        return userId;
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