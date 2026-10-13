package app.lightmove.api.candidate.dto;

import java.util.List;

/**
 * A position's whole pipeline: its executives by stage and then the team's own order, and a count per live
 * stage. The counts honour the search box and not the stage filter, so every pill keeps its number.
 */
public record PositionPipelineResponse(List<PipelineRowResponse> rows, List<PipelineStageCountResponse> stageCounts,
                                       long total) {}
