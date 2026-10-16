package app.uncava.api.notification.dto;

import java.util.List;
import java.util.UUID;

/** Marks the named rows read, or every row when {@code all}. */
public record MarkNotificationsReadRequest(List<UUID> ids, Boolean all) {}
