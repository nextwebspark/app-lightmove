package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.Map;

/**
 * One page of the position's Candidates page. {@code statusCounts} is keyed by {@code CandidateStatus}
 * wire token and counts the search without the status filter, so every chip states what it would show.
 */
public record CandidatePipelineResponse(List<CandidateResponse> candidates, Map<String, Long> statusCounts,
                                        long totalCount, int page, int size) {}
