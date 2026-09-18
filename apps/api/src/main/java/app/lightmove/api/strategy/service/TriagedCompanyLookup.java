package app.lightmove.api.strategy.service;

import app.lightmove.api.strategy.model.CompanyExclusion;
import java.util.UUID;

/**
 * The one fact {@code strategy} needs from a mandate's triaged companies — a predicate excluding
 * whichever ones it has already triaged — without importing {@code triagecompany}. {@code
 * triagecompany} implements this, so the dependency direction the two packages document (it depends
 * on {@code strategy}, never the reverse) still holds: this interface is {@code strategy}'s own, and
 * the only thing crossing the boundary the other way is a bean satisfying it.
 *
 * <p>The answer is a {@link CompanyExclusion} rather than an id list: {@code strategy} composes the
 * predicate into its own query without ever holding — or re-binding — a mandate's whole triage
 * history in Java.
 */
public interface TriagedCompanyLookup {

    /** A predicate excluding every company this project has already triaged, at any stage. */
    CompanyExclusion exclusionFor(UUID projectId);
}
