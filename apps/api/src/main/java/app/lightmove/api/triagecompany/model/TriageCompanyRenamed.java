package app.lightmove.api.triagecompany.model;

import java.util.UUID;

/** A mandate-supplied company's name was corrected, published inside the editing transaction. */
public record TriageCompanyRenamed(UUID projectId, UUID triageCompanyId, String companyName) {}
