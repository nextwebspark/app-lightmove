package app.lightmove.api.triagecompany.model;

import java.util.UUID;

/** A captured company the market could not resolve, with a LinkedIn page for the enrichment worker. */
public record TriageCompanyCapturedEvent(UUID companyId, UUID projectId, String linkedinSlug) {}
