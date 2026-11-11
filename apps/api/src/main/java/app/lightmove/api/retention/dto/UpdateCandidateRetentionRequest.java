package app.lightmove.api.retention.dto;

/** One of the offered periods in months, or null for never. */
public record UpdateCandidateRetentionRequest(Integer months) {}
