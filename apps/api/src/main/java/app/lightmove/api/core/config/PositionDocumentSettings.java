package app.lightmove.api.core.config;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The position description a mandate can attach to its brief. One small document per position, stored
 * in the row rather than in object storage — this is a single file per mandate, not a library.
 *
 * <p>The allowlist is checked <b>server-side</b>: the content type on a multipart part is a claim made
 * by whatever sent the request, so it decides nothing on its own.
 */
public record PositionDocumentSettings(
        /**
         * Ceiling on one upload, in bytes. Deliberately modest: the bytes travel through the same
         * connection pool as every query, and a position description is a handful of pages.
         */
        @DefaultValue("10485760") long maxFileSizeBytes,

        /** The document types a position description may arrive as. */
        @DefaultValue({
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain"
        }) List<String> allowedContentTypes
) {

    public PositionDocumentSettings {
        if (maxFileSizeBytes < 1) {
            throw new IllegalArgumentException(
                    "lightmove.position.document.max-file-size-bytes must be positive, but was "
                            + maxFileSizeBytes);
        }
        // @DefaultValue on a List binds an operator's empty override to [""], not to [] — the trap that
        // once emptied the consumer-domain blocklist. An allowlist of one blank string accepts nothing,
        // so it is refused loudly here rather than silently rejecting every upload.
        if (allowedContentTypes.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "lightmove.position.document.allowed-content-types must not contain a blank entry");
        }
        allowedContentTypes = List.copyOf(allowedContentTypes);
    }

    public boolean allows(String contentType) {
        return contentType != null && Set.copyOf(allowedContentTypes).contains(contentType);
    }
}
