package app.uncava.api.task.dto;

import java.util.UUID;

/** A teammate as a task names them; the SPA always draws them with their avatar. */
public record TaskPersonDto(UUID userId, String fullName, String avatarUrl) {}
