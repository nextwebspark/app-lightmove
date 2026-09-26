package app.lightmove.api.strategy.service;

import app.lightmove.api.strategy.model.CompanyExclusion;
import java.util.UUID;

/**
 * {@code strategy}'s own interface, implemented by {@code triagecompany}, so the search can exclude
 * already-triaged companies without depending on it. Answers a predicate, not an id list.
 */
public interface TriagedCompanyLookup {

    CompanyExclusion exclusionFor(UUID projectId);
}
