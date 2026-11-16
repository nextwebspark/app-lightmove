package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.PersonNote;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * A note to write or revise. {@code projectId} (workspace routes) or {@code allPositions} (a position's route) says what
 * it is about, never read on a revise; {@code format} and {@code kind} default to plain. A logged entry may carry
 * {@code occurredAt}, a {@code title}, and for a meeting a {@code meetingId} or {@code recordingId}, never both. A call
 * or WhatsApp log may carry an {@code outcome}, and with one the {@code body} may be empty.
 */
public record WritePersonNoteRequest(
        String kind,
        @Size(max = PersonNote.MAX_BODY) String body,
        UUID projectId,
        Boolean allPositions,
        String format,
        Instant occurredAt,
        @Size(max = PersonNote.MAX_TITLE) @Pattern(regexp = "[^\\r\\n]*", message = "A title is one line") String title,
        UUID meetingId,
        UUID recordingId,
        String outcome
) {

    public String bodyText() {
        return body == null ? "" : body.strip();
    }

    /** An edit that sends no body keeps the words held, as one sending no kind keeps the kind. */
    public String bodyTextOr(String current) {
        return body == null ? current : body.strip();
    }

    public boolean isForAllPositions() {
        return Boolean.TRUE.equals(allPositions);
    }

    @AssertTrue(message = "A note is about one position or about every position, not both")
    public boolean isAboutOneScope() {
        return !isForAllPositions() || projectId == null;
    }
}
