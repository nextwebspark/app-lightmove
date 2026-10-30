package app.lightmove.api.pairing.dto;

import app.lightmove.api.candidate.dto.AddedByResponse;
import app.lightmove.api.triagecompany.dto.TriageCountsDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One page of a stage's grid lines, ordered across the whole stage. {@code addedBy}, keyed by candidate id, is a staff
 * read's alone and null otherwise. {@code truncated} says the stage ran past what one read pairs — its companies, its
 * people or both — so the order covers only what fit under those ceilings.
 */
public record StageLinesResponse(List<StageLineResponse> lines, Map<UUID, AddedByResponse> addedBy, long totalCount,
                                 int page, int size, TriageCountsDto counts, boolean truncated) {}
