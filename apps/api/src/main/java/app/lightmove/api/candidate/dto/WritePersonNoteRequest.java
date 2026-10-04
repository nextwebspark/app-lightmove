package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.PersonNote;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A note to write or revise. {@code projectId} names the position it is about, and is read only on the
 * workspace's routes: under a position's own route the note is about that position.
 */
public record WritePersonNoteRequest(
        @NotBlank String kind,
        @NotBlank @Size(max = PersonNote.MAX_BODY) String body,
        UUID projectId
) {}
