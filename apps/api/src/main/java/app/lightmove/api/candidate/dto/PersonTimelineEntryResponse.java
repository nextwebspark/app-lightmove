package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One line of a person's history: who, what, when, on which position. Its details are an allowlist —
 * a door, a status move, a count — and a note line carries the live note's opening words, never a copy.
 */
public record PersonTimelineEntryResponse(
        long id,
        /** A {@code PersonActivityKind} name. */
        String kind,
        Instant occurredAt,
        UUID actorUserId,
        String actorName,
        String actorAvatarUrl,
        UUID personId,
        String personName,
        UUID projectId,
        String projectTitle,
        Map<String, String> details,
        /** The note's opening words while it exists; null once it has been removed. */
        String noteExcerpt
) {}
