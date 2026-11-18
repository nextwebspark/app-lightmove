package app.lightmove.api.project.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * The names of the people a position's recent activity mentions, read from the live rows because the audit trail
 * holds their ids alone (#927). Implemented in {@code candidate}; a person no longer held, erased included, is absent.
 */
@NamedInterface(name = "api", propagate = false)
public interface ActivityPersonNames {

    Map<UUID, String> namesOf(UUID workspaceId, Collection<UUID> personIds);
}
