package app.uncava.api.candidate.dto;

/** How many of a position's executives stand at one live stage, zero included. */
public record PipelineStageCountResponse(String key, String label, String colour, String kind, int position,
                                         long count) {}
