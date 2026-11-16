package app.lightmove.api.enrichment.candidate.dto;

import app.lightmove.api.candidate.dto.HeldCandidateResponse;

/** One lookup's answer: {@code profile} on found, {@code held} when this position already maps them. */
public record ProfileLookupResponse(String outcome, LookedUpProfileResponse profile, HeldCandidateResponse held) {}
