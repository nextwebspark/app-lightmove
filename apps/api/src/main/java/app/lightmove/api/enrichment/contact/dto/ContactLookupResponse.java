package app.lightmove.api.enrichment.contact.dto;

import app.lightmove.api.candidate.dto.CandidateResponse;

/**
 * What one press of a Find button did, and the row as it now reads.
 *
 * <p>The whole candidate rather than the contacts alone, so the drawer replaces its cached row and the
 * grid re-renders the promoted email or phone in one round trip.
 */
public record ContactLookupResponse(String outcome, CandidateResponse candidate) {}
