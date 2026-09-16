package app.lightmove.api.strategy.service;

import java.util.List;
import java.util.UUID;

/**
 * The one fact {@code strategy} needs from a mandate's triaged companies — which ids to leave out of
 * its own search — without importing {@code triagecompany}. {@code triagecompany} implements this,
 * so the dependency direction the two packages document (it depends on {@code strategy}, never the
 * reverse) still holds: this interface is {@code strategy}'s own, and the only thing crossing the
 * boundary the other way is a bean satisfying it.
 */
public interface TriagedCompanyLookup {

    /** Every apolloAccountId this project has already triaged, at any stage. */
    List<String> accountIdsFor(UUID projectId);
}
