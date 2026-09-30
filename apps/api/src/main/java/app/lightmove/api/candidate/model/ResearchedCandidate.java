package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;

/** A filed search hit, and the employer it was filed under when the filing named one to capture. */
public record ResearchedCandidate(CandidateResponse candidate, TriageCompanyResponse employer) {}
