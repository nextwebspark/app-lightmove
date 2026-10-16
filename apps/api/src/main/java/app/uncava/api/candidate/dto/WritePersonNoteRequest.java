package app.uncava.api.candidate.dto;

import app.uncava.api.candidate.model.PersonNote;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A note to write or revise. On the workspace's routes {@code projectId} names the position it is about;
 * under a position's own route the note is about that position unless {@code allPositions} says it is about
 * the person, on every position. Neither is read on a revise: what a note is about never changes.
 */
public record WritePersonNoteRequest(
        @NotBlank String kind,
        @NotBlank @Size(max = PersonNote.MAX_BODY) String body,
        UUID projectId,
        Boolean allPositions
) {

    public boolean isForAllPositions() {
        return Boolean.TRUE.equals(allPositions);
    }

    @AssertTrue(message = "A note is about one position or about every position, not both")
    public boolean isAboutOneScope() {
        return !isForAllPositions() || projectId == null;
    }
}
