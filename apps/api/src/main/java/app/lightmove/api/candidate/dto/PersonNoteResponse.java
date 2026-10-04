package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/** A note on a person, as the drawer lists it. Staff-only: no client seat route returns one. */
public record PersonNoteResponse(
        UUID id,
        /** A {@code PersonNoteKind} wire token. */
        String kind,
        String body,
        boolean pinned,
        /** The position the note was written about, or null for a note about the person. */
        UUID projectId,
        String projectTitle,
        UUID authorUserId,
        String authorName,
        String authorAvatarUrl,
        Instant createdAt,
        Instant editedAt,
        String editedByName,
        /** Whether the caller may revise or remove it: its author, or a workspace admin. */
        boolean editable
) {}
