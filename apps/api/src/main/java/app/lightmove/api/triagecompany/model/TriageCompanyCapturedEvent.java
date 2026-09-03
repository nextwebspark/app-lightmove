package app.lightmove.api.triagecompany.model;

import java.util.UUID;

/**
 * A plugin capture put a company in the universe that the market could not resolve — a hand-shaped
 * row with a LinkedIn page worth researching. Published by {@code TriageCompanyService} for both
 * doors (the company tab, and an executive's employer resolved by candidate research), consumed
 * after the commit by the company enrichment worker. Primitives only, mirroring
 * {@code CandidateCapturedEvent}.
 */
public record TriageCompanyCapturedEvent(UUID companyId, UUID projectId, String linkedinSlug) {}
