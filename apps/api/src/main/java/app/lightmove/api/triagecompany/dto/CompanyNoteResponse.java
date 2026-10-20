package app.lightmove.api.triagecompany.dto;

import java.time.Instant;
import java.util.UUID;

/** A note on a company, as the company drawer lists it. Staff-only: no client seat route returns one. */
public record CompanyNoteResponse(
        UUID id,
        /** A {@code CompanyNoteKind} wire token. */
        String kind,
        String body,
        /** A {@code NoteFormat} wire token: how {@code body} is to be read. */
        String format,
        boolean pinned,
        UUID authorUserId,
        String authorName,
        String authorAvatarUrl,
        Instant createdAt,
        Instant editedAt,
        String editedByName,
        /** Whether the caller may revise, remove or restore it: its author, or a workspace admin. */
        boolean editable
) {}
