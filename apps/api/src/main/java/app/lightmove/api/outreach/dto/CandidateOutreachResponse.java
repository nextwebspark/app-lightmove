package app.lightmove.api.outreach.dto;

import java.util.List;

/** The executive drawer's Outreach section: their latest run on this position, or none. */
public record CandidateOutreachResponse(OutreachRunResponse run, List<OutreachStepStateResponse> steps) {}
