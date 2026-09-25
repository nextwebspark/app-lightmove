package app.lightmove.api.report.dto;

/** How many executives stand at one {@code CandidateStatus} wire token — a mix, not a funnel. */
public record StatusCountDto(String status, int count) {}
