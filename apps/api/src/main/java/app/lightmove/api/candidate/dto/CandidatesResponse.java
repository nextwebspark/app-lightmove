package app.lightmove.api.candidate.dto;

import java.util.List;

/** One page of a mandate's mapped executives. */
public record CandidatesResponse(List<CandidateResponse> candidates, long totalCount, int page,
                                 int size) {}
