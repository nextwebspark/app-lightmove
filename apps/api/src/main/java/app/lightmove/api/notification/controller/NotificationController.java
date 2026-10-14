package app.lightmove.api.notification.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.notification.dto.ChangeNotificationRequest;
import app.lightmove.api.notification.dto.MarkNotificationsReadRequest;
import app.lightmove.api.notification.dto.NotificationPreferencesDto;
import app.lightmove.api.notification.dto.NotificationResponse;
import app.lightmove.api.notification.dto.NotificationsResponse;
import app.lightmove.api.notification.dto.UnreadCountResponse;
import app.lightmove.api.notification.service.NotificationPreferenceService;
import app.lightmove.api.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own bell and its settings, in the session's workspace. Staff only — a pure client has no bell,
 * and is told none exists. The live stream is {@code core/stream}'s {@code UserStreamController}.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;
    private final NotificationPreferenceService preferences;

    @GetMapping("/notifications")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public NotificationsResponse page(@AuthenticationPrincipal AuthPrincipal principal,
                                      @RequestParam(required = false) String filter,
                                      @RequestParam(required = false) String cursor) {
        return notifications.page(principal.userId(), principal.requireWorkspaceId(), filter, cursor);
    }

    @GetMapping("/notifications/unread-count")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public UnreadCountResponse unread(@AuthenticationPrincipal AuthPrincipal principal) {
        return notifications.unread(principal.userId(), principal.requireWorkspaceId());
    }

    @PostMapping("/notifications/read")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public UnreadCountResponse markRead(@AuthenticationPrincipal AuthPrincipal principal,
                                        @RequestBody MarkNotificationsReadRequest request) {
        return notifications.markRead(principal.userId(), principal.requireWorkspaceId(), request);
    }

    @PatchMapping("/notifications/{notificationId}")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public NotificationResponse change(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID notificationId,
                                       @RequestBody ChangeNotificationRequest request) {
        return notifications.change(principal.userId(), principal.requireWorkspaceId(), notificationId, request);
    }

    @GetMapping("/notification-preferences")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public NotificationPreferencesDto preferences(@AuthenticationPrincipal AuthPrincipal principal) {
        return preferences.of(principal.userId(), principal.requireWorkspaceId());
    }

    @PutMapping("/notification-preferences")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public NotificationPreferencesDto changePreferences(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @Valid @RequestBody NotificationPreferencesDto request) {
        return preferences.change(principal.userId(), principal.requireWorkspaceId(), request);
    }
}
