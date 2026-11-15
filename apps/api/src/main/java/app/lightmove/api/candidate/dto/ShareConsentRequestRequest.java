package app.lightmove.api.candidate.dto;

/** Asking a person on a position to agree to their profile being shared; {@code nameClient} null or false says "a confidential client". */
public record ShareConsentRequestRequest(Boolean nameClient) {}
