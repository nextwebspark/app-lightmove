package app.lightmove.api.candidate.dto;

import java.util.List;

/**
 * A position's whole pipeline: its executives by stage and then the team's own order, a count per live
 * stage of those still in process, and how many are out of it ({@code inactiveCount}). The counts honour the
 * search box and not the stage filter, so every pill keeps its number.
 */
public record PositionPipelineResponse(List<PipelineRowResponse> rows, List<PipelineStageCountResponse> stageCounts,
                                       long inactiveCount, long total) {}
