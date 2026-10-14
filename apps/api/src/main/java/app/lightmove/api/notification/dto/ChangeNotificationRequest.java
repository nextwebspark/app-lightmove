package app.lightmove.api.notification.dto;

import java.time.Instant;

/**
 * A change to one row; every field optional. {@code snoozedUntil} hides it until then and {@code clearSnooze}
 * brings it back; {@code archived} takes it out of the bell for good.
 */
public record ChangeNotificationRequest(Boolean read, Instant snoozedUntil, Boolean clearSnooze, Boolean archived) {}
