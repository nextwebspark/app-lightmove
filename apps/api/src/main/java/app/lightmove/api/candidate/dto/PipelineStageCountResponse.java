package app.lightmove.api.candidate.dto;

/**
 * A column of the board: a stage with a live status in process, or one someone still stands at in process, and how
 * many do. A stage holding only ways out is never one.
 */
public record PipelineStageCountResponse(String key, String label, String colour, int position, long count) {}
