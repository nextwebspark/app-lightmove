package app.uncava.api.candidate.dto;

/** How many people each quick view would show under the other filters in force. */
public record CandidatePoolViewCountsResponse(long all, long mine, long active, long unplaced) {}
