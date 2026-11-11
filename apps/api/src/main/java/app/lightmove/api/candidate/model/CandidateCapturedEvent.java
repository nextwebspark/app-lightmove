package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * The plugin captured an executive with a researchable profile URL; published after save, consumed after commit by the
 * enrichment worker. Carries the project id because every candidate finder is project-scoped.
 */
public record CandidateCapturedEvent(UUID candidateId, UUID projectId, String linkedinUrl) {}
