package app.lightmove.api.triagecompany.model;

import java.util.UUID;

/**
 * A company is about to leave a mandate, published inside the removing transaction before its row goes, so everyone
 * mapped at it leaves with it rather than staying behind with no company.
 */
public record TriageCompanyRemoved(UUID projectId, UUID workspaceId, UUID triageCompanyId, UUID removedBy) {}
