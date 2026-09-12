package app.lightmove.api.project.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * How many executives each mandate has mapped — the number the projects list and its drawer report as
 * "Candidates".
 *
 * <p>Declared here and implemented in {@code candidate} for the reason given on
 * {@link ProjectCompanyCounter}: the people side already reads the project, and the project must not
 * read back into it.
 */
public interface ProjectCandidateCounter {

    /** Counts by project id. A mandate with nobody mapped is absent from the map rather than zero. */
    Map<UUID, Long> countByProject(Collection<UUID> projectIds);
}
