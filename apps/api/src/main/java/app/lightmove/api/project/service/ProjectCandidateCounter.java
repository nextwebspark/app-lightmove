package app.lightmove.api.project.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The projects list's people-side numbers: how many executives each mandate still has in play, how
 * many of them are engaged, and how many of its universe companies have anyone mapped at all — the
 * last being the side panel's mapping coverage against {@link ProjectCompanyCounter}'s universe.
 *
 * <p>Declared here and implemented in {@code candidate} for the reason given on
 * {@link ProjectCompanyCounter}: the people side already reads the project, and the project must not
 * read back into it. Every method answers by project id, and a mandate with nothing to count is absent
 * from the map rather than zero.
 */
public interface ProjectCandidateCounter {

    /**
     * Executives still in the running — "Candidates" on the list. Those who have left it are left
     * out, matching what {@link ProjectCompanyCounter} does with declined companies: the two sit side
     * by side under one "Pipeline" heading.
     */
    Map<UUID, Long> countByProject(Collection<UUID> projectIds);

    /** Executives who have answered: engaged or interested. */
    Map<UUID, Long> countEngagedByProject(Collection<UUID> projectIds);

    /**
     * Universe companies with at least one executive mapped at them. Declined companies are left out
     * for the same reason they are left out of the universe count this is read against — otherwise
     * coverage could pass 100%.
     */
    Map<UUID, Long> countMappedCompaniesByProject(Collection<UUID> projectIds);
}
