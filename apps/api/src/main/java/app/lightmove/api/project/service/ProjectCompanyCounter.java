package app.lightmove.api.project.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * How many companies each mandate holds in its universe — the number the projects list and its drawer
 * report as "Companies".
 *
 * <p>Declared here and implemented in {@code triagecompany} because the dependency runs that way: the
 * triage side already reads the project, so a project reading back into it would close the loop.
 */
public interface ProjectCompanyCounter {

    /** Counts by project id. A mandate holding none is absent from the map rather than zero. */
    Map<UUID, Long> countByProject(Collection<UUID> projectIds);
}
