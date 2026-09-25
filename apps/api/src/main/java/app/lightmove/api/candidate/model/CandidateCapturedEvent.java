package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * The plugin captured an executive with a profile URL worth researching. Published by
 * {@code CandidateService} after the row is saved, and consumed after the commit by the enrichment
 * worker — primitives only, mirroring {@code ClientRepresentativeAcceptedEvent}.
 *
 * <p>Carries the project id because every candidate finder is project-scoped by design; a worker
 * looking a row up by id alone would be the one read in the codebase that is not.
 */
public record CandidateCapturedEvent(UUID candidateId, UUID projectId, String linkedinUrl) {}
