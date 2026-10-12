package app.lightmove.api.triagecompany.service;

import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The seam {@code triagecompany} reads executive facts through; {@code candidate} implements it, so
 * {@code triagecompany} never depends on {@code candidate}. The ranked read pages itself because that
 * data lives on the other side. Every read names its audience: an executive hidden from the client
 * neither matches a filter nor moves a sort for a client seat.
 */
public interface MappedExecutiveLookup {

    /** Case-insensitive substring match. Never asked with a blank name. */
    Set<UUID> triageCompanyIdsMatchingExecutiveName(UUID projectId, String executiveName,
                                                    ProjectAudience audience);

    /** Never asked with an empty list. */
    Set<UUID> triageCompanyIdsWithExecutiveStatusIn(UUID projectId, List<String> executiveStatuses,
                                                    ProjectAudience audience);

    /** Which of these keys the workspace's pipeline does not hold — a filter naming one is a 400. */
    List<String> unknownExecutiveStatuses(UUID workspaceId, UUID projectId, Collection<String> executiveStatuses);

    /**
     * Ranked by each company's best (lowest) executive status; a company with no executive ranks last
     * either way. Honours the same filters as the ordinary listing.
     */
    Page<UUID> triageCompanyIdsRankedByExecutiveStatus(UUID projectId, TriageCompanyStatus status,
            String companyName, String executiveName, List<String> executiveStatuses,
            boolean ascending, Pageable pageable, ProjectAudience audience);
}
