package app.uncava.api.task.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One task as every screen draws it. Staff-only. {@code createdBy} is null when a rule filed it;
 * {@code editable} says whether the caller may change, reassign or cancel it; {@code note} is the note it came from.
 */
public record TaskResponse(
        UUID id,
        UUID projectId,
        String projectTitle,
        UUID candidateId,
        UUID personId,
        String personName,
        String title,
        String kind,
        String detail,
        UUID presetId,
        TaskNoteDto note,
        TaskPersonDto assignee,
        TaskPersonDto createdBy,
        Instant dueAt,
        String status,
        String source,
        String rule,
        Instant completedAt,
        TaskPersonDto completedBy,
        Instant createdAt,
        boolean editable
) {}
