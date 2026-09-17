package app.lightmove.api.candidate.dto;

import java.time.Instant;

/**
 * One address the mandate knows. {@code kind} is {@code "work"} or {@code "personal"} — null where
 * no provider said which, including everything a person typed — {@code status} the provider's own
 * word for it, and {@code source} the door it came through ({@code "manual"}, {@code "csv"},
 * {@code "extension"}, {@code "contactout"}).
 */
public record CandidateEmailDto(String address, String kind, boolean verified, String status,
                                String source, Instant foundAt) {}
