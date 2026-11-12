package app.lightmove.api.triagecompany.model;

import java.util.UUID;

/** Someone deliberately added or edited a company the mandate already held, published inside that transaction. */
public record TriageCompanyWorked(UUID projectId, UUID triageCompanyId) {}
