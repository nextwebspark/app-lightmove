package app.lightmove.api.notification.dto;

import app.lightmove.api.task.dto.TaskPersonDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One bell row. The SPA composes its sentence from {@code kind}; a note is named by id and never quoted.
 * {@code actor} is null when Uncava did it; {@code names} are a grouped row's latest people, newest first.
 */
public record NotificationResponse(
        UUID id,
        String kind,
        int count,
        TaskPersonDto actor,
        UUID projectId,
        String projectTitle,
        UUID personId,
        UUID candidateId,
        String personName,
        UUID taskId,
        String taskTitle,
        UUID noteId,
        List<String> names,
        Instant dueAt,
        Instant createdAt,
        boolean read,
        Instant snoozedUntil
) {}
