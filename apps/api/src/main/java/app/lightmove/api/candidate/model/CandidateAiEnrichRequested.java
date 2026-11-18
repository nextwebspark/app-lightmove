package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * Asks for a candidate's automatic AI run once the publishing transaction commits. Ids only: the worker reads the
 * row as it then stands.
 */
@NamedInterface(name = "api", propagate = false)
public record CandidateAiEnrichRequested(UUID candidateId, UUID projectId, UUID workspaceId,
                                         UUID requestedBy, AiEnrichTrigger trigger) {}
