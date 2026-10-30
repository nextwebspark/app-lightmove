package app.lightmove.api.candidate.dto;

/**
 * The stage a candidate's status sits under, as every read of a candidate carries it: derived from the
 * status, never stored beside it. Whether they are still in process is the status's own flag.
 */
public record CandidatePipelineStageDto(String key, String label, String colour, int position) {}
