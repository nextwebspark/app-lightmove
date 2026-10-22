package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.UUID;

/**
 * A company of a mandate moved stage. Published inside the moving transaction, so whoever keeps people at that
 * company (#903: they move with it) writes in the same commit.
 */
public record TriageCompanyMoved(UUID projectId, UUID triageCompanyId, TriageCompanyStatus status) {}
