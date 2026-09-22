package app.lightmove.api.project.service;

import app.lightmove.api.project.model.ProjectProgressCounts;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The raw numbers each mandate's progress bar and health are measured from.
 *
 * <p>Declared here and implemented in {@code candidate} for {@link ProjectCompanyCounter}'s reason:
 * both of the features that hold these rows already read the project repository, so a call out of
 * {@code project} would close the loop.
 *
 * <p>One interface rather than one per feature because one feature can answer both halves. Whether a
 * company has been researched spans two tables — {@code no_executive_found} is the triage row's and
 * "has an executive" is the candidate's — and {@code candidate} is the side allowed to read the
 * other's table directly, which is why the combined count lives there.
 *
 * <p>Deliberately separate from {@link ProjectCompanyCounter} and {@link ProjectCandidateCounter}:
 * those answer the list's headline counts under their own policies (declined companies out,
 * executives who left the running out), and folding them together would make one number mean two
 * things.
 */
public interface ProjectProgressCounter {

    /** Counts by project id. A mandate with nothing to count is absent from the map rather than zero. */
    Map<UUID, ProjectProgressCounts> countByProject(Collection<UUID> projectIds);
}
