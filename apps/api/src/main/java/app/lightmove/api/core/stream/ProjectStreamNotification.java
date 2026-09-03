package app.lightmove.api.core.stream;

import java.util.UUID;

/** The {@code pg_notify} payload: which mandate changed and how. */
public record ProjectStreamNotification(UUID projectId, String kind) {}
