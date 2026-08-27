package app.lightmove.api.position.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored document without its bytes.
 *
 * <p>A closed projection rather than a convenience: the file card renders on every read of the brief,
 * and loading the entity would drag up to ten megabytes of {@code bytea} through the connection pool
 * on every page load. {@code @Basic(fetch = LAZY)} on a {@code byte[]} is not honoured without
 * bytecode enhancement, so leaving the column out of the query is the only thing that actually works.
 */
public interface PositionDocumentSummary {

    UUID getId();

    String getFileName();

    String getContentType();

    long getFileSize();

    Instant getCreatedAt();
}
