package com.marketplace.backend.admin;

import com.marketplace.backend.common.ApiListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<ApiListResponse<AdminUserResponse>> getUsers() {
        List<AdminUserResponse> users = adminUserService.getUsers();

        return ResponseEntity.ok(
                new ApiListResponse<>(
                        users,
                        1,
                        users.size(),
                        users.size()
                )
        );
    }

    @PatchMapping("/{userId}/suspend")
    public ResponseEntity<AdminUserResponse> suspendUser(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(
                adminUserService.suspendUser(userId)
        );
    }

    @PatchMapping("/{userId}/activate")
    public ResponseEntity<AdminUserResponse> activateUser(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(
                adminUserService.activateUser(userId)
        );
    }
}