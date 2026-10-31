package app.lightmove.api.triagecompany.model;

import java.util.UUID;

/** A company is about to leave a mandate, published in the removing transaction before its row goes. */
public record TriageCompanyRemoved(UUID projectId, UUID workspaceId, UUID triageCompanyId, UUID removedBy) {}
