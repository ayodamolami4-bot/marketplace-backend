package com.marketplace.backend.admin;

import com.marketplace.backend.user.UserStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String name,
        List<String> roles,
        UserStatus status,
        boolean emailVerified,
        LocalDateTime createdAt
) {
}