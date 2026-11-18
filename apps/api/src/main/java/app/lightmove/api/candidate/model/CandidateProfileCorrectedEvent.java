package app.lightmove.api.candidate.model;

import java.util.UUID;

/** A researcher corrected a researched person's LinkedIn address to another human's; the new profile is researched. */
public record CandidateProfileCorrectedEvent(UUID candidateId, UUID projectId, String linkedinUrl) {}
