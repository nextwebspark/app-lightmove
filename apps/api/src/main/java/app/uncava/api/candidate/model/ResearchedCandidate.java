package app.uncava.api.candidate.model;

import app.uncava.api.candidate.dto.CandidateResponse;
import app.uncava.api.triagecompany.dto.TriageCompanyResponse;

/** A filed search hit, and the employer it was filed under when the filing named one to capture. */
public record ResearchedCandidate(CandidateResponse candidate, TriageCompanyResponse employer) {}
