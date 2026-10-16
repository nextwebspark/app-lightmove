package app.uncava.api.project.service;

import app.uncava.api.project.model.CandidateCountScope;
import java.util.Map;
import java.util.UUID;

/**
 * The projects list's people-side numbers, implemented in {@code candidate} so {@code project} never
 * reads back into it. A mandate with nothing to count is absent from the map, not zero.
 */
public interface ProjectCandidateCounter {

    /** Executives still in the running, as {@link ProjectCompanyCounter} leaves declined companies out. */
    Map<UUID, Long> countByProject(CandidateCountScope scope);

    /** Every executive mapped, ruled out or not — what the side panel's mapping velocity divides. */
    Map<UUID, Long> countMappedByProject(CandidateCountScope scope);

    /** Executives who have answered: engaged or interested. */
    Map<UUID, Long> countEngagedByProject(CandidateCountScope scope);

    /** Executives approached at all, answered or not — what puts a mandate into outreach. */
    Map<UUID, Long> countReachedOutByProject(CandidateCountScope scope);

    /** Companies with an executive mapped; declined ones left out, or coverage could pass 100%. */
    Map<UUID, Long> countMappedCompaniesByProject(CandidateCountScope scope);
}
