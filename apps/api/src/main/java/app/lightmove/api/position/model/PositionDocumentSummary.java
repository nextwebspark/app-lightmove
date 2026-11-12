package app.lightmove.api.position.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored document without its bytes: {@code @Basic(fetch = LAZY)} on {@code byte[]} is not honoured without
 * bytecode enhancement, so leaving the column out of the query is the only way.
 */
public interface PositionDocumentSummary {

    UUID getId();

    String getFileName();

    String getContentType();

    long getFileSize();

    Instant getCreatedAt();
}
