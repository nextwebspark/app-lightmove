package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A document on a person with its files, newest first. Staff-only: no client seat route returns one. */
public record PersonDocumentResponse(
        UUID id,
        /** A {@code PersonDocumentCategory} wire token. */
        String category,
        String title,
        boolean primaryCv,
        /** The position it was uploaded through, or null when it came in on the workspace's routes. */
        UUID projectId,
        String projectTitle,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        Instant updatedAt,
        /** Whether the caller may remove the whole document: whoever filed it, or a workspace admin. */
        boolean removable,
        List<PersonDocumentVersionResponse> versions
) {}
