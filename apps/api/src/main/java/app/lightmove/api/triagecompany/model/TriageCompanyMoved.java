package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.Set;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * A company of a mandate moved stage, published inside the moving transaction. {@code executiveIds} names exactly who
 * moves with it — an Undo restoring the people the move carried — and null means everyone still at {@code from}, so a
 * person moved on their own keeps their stage (#903).
 */
@NamedInterface(name = "api", propagate = false)
public record TriageCompanyMoved(UUID projectId, UUID triageCompanyId, TriageCompanyStatus from,
                                 TriageCompanyStatus to, UUID movedBy, Set<UUID> executiveIds) {}
