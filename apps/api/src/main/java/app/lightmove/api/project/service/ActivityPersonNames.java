package app.lightmove.api.project.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The names of the people a position's recent activity mentions, read from the live rows because the audit trail
 * holds their ids alone (#927). Implemented in {@code candidate}; a person no longer held, erased included, is absent.
 */
public interface ActivityPersonNames {

    Map<UUID, String> namesOf(UUID workspaceId, Collection<UUID> personIds);
}
