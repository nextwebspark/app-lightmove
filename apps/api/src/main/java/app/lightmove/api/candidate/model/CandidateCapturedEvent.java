package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * The plugin captured an executive with a profile URL worth researching. Published by
 * {@code CandidateService} after the row is saved, and consumed after the commit by the enrichment
 * worker — primitives only, mirroring {@code ClientRepresentativeAcceptedEvent}.
 *
 * <p>Carries the project id because every candidate finder is project-scoped by design; a worker
 * looking a row up by id alone would be the one read in the codebase that is not.
 *
 * <p>{@code addedBy} and {@code fullName} exist for {@code CandidateBackgroundProposer} (issue #458):
 * the budget it spends is metered per acting user, and its prompt names the person the profile
 * belongs to. Both are cheap to carry and spare the worker a lookup it would otherwise need before it
 * could even decide whether to call the model.
 */
public record CandidateCapturedEvent(UUID candidateId, UUID projectId, String linkedinUrl,
                                     UUID addedBy, String fullName) {}
