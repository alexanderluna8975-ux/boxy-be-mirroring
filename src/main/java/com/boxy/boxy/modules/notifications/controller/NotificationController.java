package com.boxy.boxy.modules.notifications.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.core.response.PageMeta;
import com.boxy.boxy.modules.notifications.dto.NotificationDto;
import com.boxy.boxy.modules.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The signed-in user's own notifications — nothing here takes a user id: every operation acts on
 * whoever is authenticated, so there is no path to another user's data. Any authenticated user may
 * use it (no specific permission), which is why the guard is {@code isAuthenticated()} rather than
 * a permission code.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notifications", description = "The current user's in-app notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List my notifications, newest first")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        Page<NotificationDto> result = notificationService.list(unreadOnly, PageRequest.of(Math.max(page, 1) - 1, size));
        return ResponseEntity.ok(ApiResponse.paged(result.getContent(), PageMeta.from(result)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "How many of my notifications are unread")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", notificationService.unreadCount())));
    }

    @PatchMapping("/{id:\\d+}/read")
    @Operation(summary = "Mark one of my notifications as read")
    public ResponseEntity<ApiResponse<NotificationDto>> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markRead(id)));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all of my notifications as read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("updated", notificationService.markAllRead())));
    }
}
