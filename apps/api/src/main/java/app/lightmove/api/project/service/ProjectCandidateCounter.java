package app.lightmove.api.project.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The projects list's people-side numbers, implemented in {@code candidate} so {@code project} never
 * reads back into it. A mandate with nothing to count is absent from the map, not zero.
 */
public interface ProjectCandidateCounter {

    /** Executives still in the running, as {@link ProjectCompanyCounter} leaves declined companies out. */
    Map<UUID, Long> countByProject(Collection<UUID> projectIds);

    /** Every executive mapped, ruled out or not — what the side panel's mapping velocity divides. */
    Map<UUID, Long> countMappedByProject(Collection<UUID> projectIds);

    /** Executives who have answered: engaged or interested. */
    Map<UUID, Long> countEngagedByProject(Collection<UUID> projectIds);

    /** Companies with an executive mapped; declined ones left out, or coverage could pass 100%. */
    Map<UUID, Long> countMappedCompaniesByProject(Collection<UUID> projectIds);
}
