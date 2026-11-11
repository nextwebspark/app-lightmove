package app.lightmove.api.candidate.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A live note, whoever wrote it and whichever position it is about ({@code null}: about the person). */
public record ArchivedNote(String kind, @Nullable String positionTitle, @Nullable String authorName,
                           Instant createdAt, @Nullable Instant editedAt, boolean pinned, String body) {
}
