package com.campusauction.notification;

import com.campusauction.auth.CurrentMarketplaceUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentMarketplaceUser currentUser;

    public NotificationController(NotificationService notificationService, CurrentMarketplaceUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<Notification> getMyNotifications(Authentication authentication) {
        return notificationService.getUserNotifications(currentUser.require(authentication).getId());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(Authentication authentication) {
        long count = notificationService.getUnreadCount(currentUser.require(authentication).getId());
        return Map.of("unreadCount", count);
    }

    @PatchMapping("/{id}/read")
    public void markAsRead(@PathVariable Long id, Authentication authentication) {
        notificationService.markAsRead(id, currentUser.require(authentication).getId());
    }

    @PostMapping("/mark-all-read")
    public void markAllRead(Authentication authentication) {
        notificationService.markAllAsRead(currentUser.require(authentication).getId());
    }
}
