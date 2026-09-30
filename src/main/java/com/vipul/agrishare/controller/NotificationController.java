package com.vipul.agrishare.controller;

import com.vipul.agrishare.dto.NotificationResponse;
import com.vipul.agrishare.security.UserPrincipal;
import com.vipul.agrishare.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal UserPrincipal user) {
        return notificationService.list(user.getId());
    }

    /** Cheap poll for the bell badge. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserPrincipal user) {
        return Map.of("count", notificationService.unreadCount(user.getId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        notificationService.markRead(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserPrincipal user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.noContent().build();
    }
}
