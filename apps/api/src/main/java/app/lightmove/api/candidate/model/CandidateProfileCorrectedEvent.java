package app.lightmove.api.candidate.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A researcher corrected a researched person's LinkedIn address to another human's; the new profile is researched. */
@NamedInterface(name = "api", propagate = false)
public record CandidateProfileCorrectedEvent(UUID candidateId, UUID projectId, String linkedinUrl) {}
