package app.lightmove.api.core.stream;

import java.util.UUID;

/** The {@code pg_notify} payload of a person's own stream: whose bell, in which workspace. */
public record UserStreamNotification(UUID userId, UUID workspaceId) {}
