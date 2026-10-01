package app.lightmove.api.candidate.dto;

import java.util.List;

/** The staff overlay for the rows a page of the position's Candidates page drew. */
public record CandidatePipelineStaffResponse(List<CandidatePipelineStaffRowResponse> rows) {}
