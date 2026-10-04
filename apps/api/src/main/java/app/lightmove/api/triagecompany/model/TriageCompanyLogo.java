package app.lightmove.api.triagecompany.model;

import java.util.UUID;

/** A mandate company row's logo, read without the rest of its snapshot. */
public record TriageCompanyLogo(UUID id, UUID projectId, String logoUrl) {}
