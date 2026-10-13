package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.Candidate;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** The next action on one executive; a null or blank {@code text} clears it, and its date with it. */
public record SetNextActionRequest(@Size(max = Candidate.MAX_NEXT_ACTION) String text, Instant dueAt) {}
