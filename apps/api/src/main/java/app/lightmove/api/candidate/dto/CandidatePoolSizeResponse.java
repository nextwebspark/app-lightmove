package app.lightmove.api.candidate.dto;

/** How many people the workspace holds — the nav's badge, without the page's filters and counts. */
public record CandidatePoolSizeResponse(long count) {}
