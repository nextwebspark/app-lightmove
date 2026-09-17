package app.lightmove.api.candidate.dto;

import java.time.Instant;

/**
 * One number the mandate knows, spelled as it arrived, and the door it came through. {@code kind}
 * is set only where a person tagged it: no provider says whether a number is a mobile or a desk.
 */
public record CandidatePhoneDto(String number, String kind, boolean verified, String status,
                                String source, Instant foundAt) {}
