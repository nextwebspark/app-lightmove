package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/** One file of a person's document. */
public record PersonDocumentVersionResponse(
        UUID id,
        int versionNo,
        String fileName,
        String contentType,
        long sizeBytes,
        UUID uploadedBy,
        String uploadedByName,
        Instant uploadedAt,
        /** A PDF or an image, which the drawer previews; anything else is downloaded. */
        boolean previewable,
        /** Whether the caller may remove this version: whoever uploaded it, or a workspace admin. */
        boolean removable
) {}
