package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.NoteOutcome;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.NamedInterface;

/** A live note, whoever wrote it and whichever position it is about ({@code null}: about the person). */
@NamedInterface(name = "api", propagate = false)
public record ArchivedNote(String kind, @Nullable String positionTitle, @Nullable String authorName,
                           Instant createdAt, @Nullable Instant editedAt, boolean pinned, String body,
                           @Nullable NoteOutcome outcome) {
}
