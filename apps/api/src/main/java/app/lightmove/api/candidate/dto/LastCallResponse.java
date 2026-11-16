package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The newest call anyone on the team logged with a person, on any position: when, how it went and who logged it.
 * Never the words or the position. Staff-only, so never on a {@code CandidateResponse}.
 */
public record LastCallResponse(
        Instant at,
        /** A {@code NoteOutcome} wire token; null for a call logged without one. */
        String outcome,
        UUID byUserId,
        /** Null once the author has left the platform. */
        String byName
) {}
