package app.lightmove.api.triagecompany.service;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The one seam {@code triagecompany} reaches into {@code candidate} through — the executive-level
 * facts the Companies grid's own Executive-name and Status filters, and its Status-rank sort, need
 * without {@code triagecompany} ever importing {@code Candidate} or querying its table. {@code candidate}
 * implements this, so the dependency direction the two packages document (it depends on
 * {@code triagecompany}, never the reverse) still holds: this interface is {@code triagecompany}'s
 * own, and the only thing crossing the boundary the other way is a bean satisfying it — the same
 * pattern {@code strategy}'s {@code TriagedCompanyLookup} already uses for its own reverse seam.
 *
 * <p>The ranked read is the one method that also does its own filtering and pagination, rather than
 * answering with an id set the way the other two do: ranking a page by data {@code triagecompany}
 * cannot see has to happen where that data lives, and {@code candidate} may reference
 * {@code app_lm_project_triage_company} directly to do it — the allowed direction — where
 * {@code triagecompany} reaching into {@code app_lm_project_candidate} to rank its own page could not.
 */
public interface MappedExecutiveLookup {

    /**
     * Ids of this project's triaged companies with at least one mapped executive whose name contains
     * this, case-insensitively. Never asked with a blank name.
     */
    Set<UUID> triageCompanyIdsMatchingExecutiveName(UUID projectId, String executiveName);

    /**
     * Ids of this project's triaged companies with at least one mapped executive in one of these
     * statuses. Never asked with an empty list.
     */
    Set<UUID> triageCompanyIdsWithExecutiveStatusIn(UUID projectId, List<String> executiveStatuses);

    /**
     * A page of this project's stage, ranked by each company's best (lowest) mapped executive status —
     * the one sort {@code TriageCompanySortField}'s flat JPA-property contract cannot express. Carries
     * the same company-name, executive-name and executive-status filters the ordinary listing does,
     * either or both optional; a company with no mapped executive at all still belongs on the page,
     * ranked last regardless of direction.
     */
    Page<UUID> triageCompanyIdsRankedByExecutiveStatus(UUID projectId, TriageCompanyStatus status,
            String companyName, String executiveName, List<String> executiveStatuses,
            boolean ascending, Pageable pageable);
}
