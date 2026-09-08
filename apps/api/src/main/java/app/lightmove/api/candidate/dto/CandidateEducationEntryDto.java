package app.lightmove.api.candidate.dto;

/**
 * One school in an education history, read-only: enrichment writes it and the profile drawer shows
 * it, and no request carries it yet. {@code period} is free text for {@link CandidateCareerEntryDto}'s
 * reason.
 */
public record CandidateEducationEntryDto(String school, String degree, String period) {}
