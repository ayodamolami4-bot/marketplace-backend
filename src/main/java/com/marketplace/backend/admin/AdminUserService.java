package com.marketplace.backend.admin;

import com.marketplace.backend.user.User;
import com.marketplace.backend.user.UserRepository;
import com.marketplace.backend.user.UserRole;
import com.marketplace.backend.user.UserRoleRepository;
import com.marketplace.backend.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public AdminUserService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse suspendUser(UUID userId) {
        User user = getUser(userId);

        user.setStatus(UserStatus.SUSPENDED);

        User savedUser = userRepository.save(user);

        return toResponse(savedUser);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }

    private AdminUserResponse toResponse(User user) {
        List<String> roles = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(UserRole::getRole)
                .map(role -> role.getName().name().toLowerCase())
                .toList();

        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                roles,
                user.getStatus(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}