package app.lightmove.api.strategy.service;

import app.lightmove.api.universe.model.CompanyExclusion;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * {@code strategy}'s own interface, implemented by {@code triagecompany}, so the search can exclude
 * already-triaged companies without depending on it. Answers a predicate, not an id list.
 */
@NamedInterface(name = "api", propagate = false)
public interface TriagedCompanyLookup {

    CompanyExclusion exclusionFor(UUID projectId);
}
