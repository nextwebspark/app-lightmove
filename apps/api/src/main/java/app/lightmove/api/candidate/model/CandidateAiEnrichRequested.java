package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import java.util.UUID;

/**
 * Asks for a candidate's automatic AI run once the publishing transaction commits. Ids only: the worker reads the
 * row as it then stands.
 */
public record CandidateAiEnrichRequested(UUID candidateId, UUID projectId, UUID workspaceId,
                                         UUID requestedBy, AiEnrichTrigger trigger) {}
