package app.lightmove.api.candidate.dto;

import java.util.UUID;

/**
 * What erasing a person deletes, counted for the confirmation an admin types their name into. {@code blockedBy} is
 * the error code the erasure would answer right now ({@code PERSON_ERASE_SEND_IN_FLIGHT}), or null.
 */
public record PersonErasurePreviewResponse(
        UUID personId,
        String fullName,
        long positions,
        long notes,
        long documents,
        long files,
        long emails,
        long outreachRuns,
        long meetings,
        long recordings,
        long tasks,
        String blockedBy
) {}
