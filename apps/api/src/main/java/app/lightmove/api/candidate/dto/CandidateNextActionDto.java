package app.lightmove.api.candidate.dto;

import java.time.Instant;

/** What happens next with an executive on one position, and who said so; {@code dueAt} is optional. */
public record CandidateNextActionDto(String text, Instant dueAt, String setByName) {}
