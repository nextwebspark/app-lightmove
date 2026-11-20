package app.lightmove.api.candidate.model;

import java.util.UUID;

/** An executive taken off a mandate, published in the removing transaction before their row goes. */
public record CandidateUnmapped(UUID workspaceId, UUID projectId, UUID candidateId, UUID personId,
                                UUID removedBy) {}
