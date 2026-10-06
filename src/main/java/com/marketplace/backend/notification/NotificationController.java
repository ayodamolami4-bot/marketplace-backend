package com.marketplace.backend.notification;

import com.marketplace.backend.common.ApiListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiListResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<NotificationResponse> notifications =
                notificationService.getUserNotifications(getUserId(jwt))
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(
                new ApiListResponse<>(
                        notifications,
                        1,
                        notifications.size(),
                        notifications.size()
                )
        );
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiListResponse<NotificationResponse>> getUnreadNotifications(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<NotificationResponse> notifications =
                notificationService.getUnreadNotifications(getUserId(jwt))
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(
                new ApiListResponse<>(
                        notifications,
                        1,
                        notifications.size(),
                        notifications.size()
                )
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        notificationService.markAsRead(
                notificationId,
                getUserId(jwt)
        );

        return ResponseEntity.noContent().build();
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}