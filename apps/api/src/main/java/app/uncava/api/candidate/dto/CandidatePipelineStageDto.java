package app.uncava.api.candidate.dto;

/**
 * The stage a candidate's status sits under, as every read of a candidate carries it: derived from the
 * status, never stored beside it. {@code kind} is {@code active} or {@code closed}.
 */
public record CandidatePipelineStageDto(String key, String label, String colour, String kind, int position) {}
