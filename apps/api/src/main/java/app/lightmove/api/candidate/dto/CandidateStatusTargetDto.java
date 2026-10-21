package app.lightmove.api.candidate.dto;

/** A status someone would be moved to, as the pipeline draws it: its key, its label and its colour token. */
public record CandidateStatusTargetDto(String statusKey, String label, String colour) {}
