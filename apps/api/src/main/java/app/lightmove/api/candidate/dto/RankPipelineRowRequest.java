package app.lightmove.api.candidate.dto;

import java.util.UUID;

/** Where a dragged row lands: between these two neighbours of its own stage, either null at an end. */
public record RankPipelineRowRequest(UUID afterCandidateId, UUID beforeCandidateId) {}
