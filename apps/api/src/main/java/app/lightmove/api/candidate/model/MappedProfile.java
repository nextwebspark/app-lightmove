package app.lightmove.api.candidate.model;

import java.util.UUID;

/** One executive a mandate maps, by the profile they were filed under. */
public record MappedProfile(UUID candidateId, String linkedinUrl) {}
