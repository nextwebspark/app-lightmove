package app.lightmove.api.candidate.dto;

/**
 * The executive a position already maps at a LinkedIn profile, with who filed them and their employer's logo (the
 * company row's, else research's): a staff read's alone.
 */
public record HeldCandidateResponse(CandidateResponse candidate, AddedByResponse addedBy, String companyLogoUrl) {}
