package app.lightmove.api.enrichment.contact.dto;

import app.lightmove.api.candidate.dto.CandidateResponse;

/** One Find press's outcome plus the whole candidate, so the drawer and grid refresh in one round trip. */
public record ContactLookupResponse(String outcome, CandidateResponse candidate) {}
