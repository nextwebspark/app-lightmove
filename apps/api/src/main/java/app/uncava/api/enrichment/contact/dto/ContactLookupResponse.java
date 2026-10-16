package app.uncava.api.enrichment.contact.dto;

import app.uncava.api.candidate.dto.CandidateResponse;

/** One Find press's outcome, the whole candidate and the workspace's credits after it: one round trip refreshes all. */
public record ContactLookupResponse(String outcome, CandidateResponse candidate, long creditsSpent, long creditsLeft) {}
