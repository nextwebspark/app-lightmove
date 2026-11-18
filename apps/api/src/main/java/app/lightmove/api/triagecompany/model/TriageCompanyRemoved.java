package app.lightmove.api.triagecompany.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A company is about to leave a mandate, published in the removing transaction before its row goes. */
@NamedInterface(name = "api", propagate = false)
public record TriageCompanyRemoved(UUID projectId, UUID workspaceId, UUID triageCompanyId, UUID removedBy) {}
