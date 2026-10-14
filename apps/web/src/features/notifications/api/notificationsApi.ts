import { request } from "../../../lib/apiClient";
import type {
  NotificationChange,
  NotificationFilter,
  NotificationPreferences,
  NotificationResponse,
  NotificationsResponse,
} from "./types";

export const NOTIFICATIONS_KEY_PREFIX = ["notifications"] as const;

export const NOTIFICATIONS_KEY = (filter: NotificationFilter) => [...NOTIFICATIONS_KEY_PREFIX, "list", filter] as const;

export const UNREAD_COUNT_KEY = [...NOTIFICATIONS_KEY_PREFIX, "unread-count"] as const;

export const NOTIFICATION_PREFERENCES_KEY = ["notification-preferences"] as const;

export function getNotifications(
  filter: NotificationFilter,
  cursor: string | null,
  signal?: AbortSignal,
): Promise<NotificationsResponse> {
  const params = new URLSearchParams({ filter });
  if (cursor) params.set("cursor", cursor);
  return request<NotificationsResponse>(`/me/notifications?${params.toString()}`, { signal });
}

export function getUnreadCount(signal?: AbortSignal): Promise<{ unreadCount: number }> {
  return request<{ unreadCount: number }>("/me/notifications/unread-count", { signal });
}

export function markRead(target: { ids: string[] } | { all: true }): Promise<{ unreadCount: number }> {
  return request<{ unreadCount: number }>("/me/notifications/read", { method: "POST", body: target });
}

export function changeNotification(id: string, change: NotificationChange): Promise<NotificationResponse> {
  return request<NotificationResponse>(`/me/notifications/${id}`, { method: "PATCH", body: change });
}

export function getNotificationPreferences(signal?: AbortSignal): Promise<NotificationPreferences> {
  return request<NotificationPreferences>("/me/notification-preferences", { signal });
}

export function putNotificationPreferences(preferences: NotificationPreferences): Promise<NotificationPreferences> {
  return request<NotificationPreferences>("/me/notification-preferences", { method: "PUT", body: preferences });
}
