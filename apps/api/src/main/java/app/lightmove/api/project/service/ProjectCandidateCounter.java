package app.lightmove.api.project.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * How many executives each mandate still has in play — the number the projects list and its drawer
 * report as "Candidates". Those who have left the running are left out, matching what
 * {@link ProjectCompanyCounter} does with declined companies: the two sit side by side under one
 * "Pipeline" heading.
 *
 * <p>Declared here and implemented in {@code candidate} for the reason given on
 * {@link ProjectCompanyCounter}: the people side already reads the project, and the project must not
 * read back into it.
 */
public interface ProjectCandidateCounter {

    /** Counts by project id. A mandate with nobody mapped is absent from the map rather than zero. */
    Map<UUID, Long> countByProject(Collection<UUID> projectIds);
}
