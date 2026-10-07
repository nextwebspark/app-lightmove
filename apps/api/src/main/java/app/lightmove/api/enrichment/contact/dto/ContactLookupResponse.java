package app.lightmove.api.enrichment.contact.dto;

import app.lightmove.api.candidate.dto.CandidateResponse;

/**
 * One Find press's outcome plus the whole candidate and the workspace's credits after it, so the drawer, the grid and
 * the credit chip refresh in one round trip.
 */
public record ContactLookupResponse(String outcome, CandidateResponse candidate, long creditsSpent, long creditsLeft) {}
