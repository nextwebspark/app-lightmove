package app.lightmove.api.candidate.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * The plugin captured an executive with a researchable profile URL; published after save, consumed after commit by the
 * enrichment worker. Carries the project id because every candidate finder is project-scoped.
 */
@NamedInterface(name = "api", propagate = false)
public record CandidateCapturedEvent(UUID candidateId, UUID projectId, String linkedinUrl) {}
