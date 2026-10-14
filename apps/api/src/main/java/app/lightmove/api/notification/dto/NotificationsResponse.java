package app.lightmove.api.notification.dto;

import java.util.List;

/** A page of the bell, the cursor to the next, and how many rows are unread in all. */
public record NotificationsResponse(List<NotificationResponse> items, String nextCursor, long unreadCount) {}
