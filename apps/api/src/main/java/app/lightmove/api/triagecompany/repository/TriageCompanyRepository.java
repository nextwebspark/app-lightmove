package app.lightmove.api.triagecompany.repository;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.TriageCompany;
import app.lightmove.api.triagecompany.model.TriageCompanyCount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every finder carries the project id — an unscoped lookup must not exist. The project is resolved
 * against the caller's workspace one layer up.
 */
public interface TriageCompanyRepository extends JpaRepository<TriageCompany, UUID> {

    Page<TriageCompany> findByProjectIdAndStatus(UUID projectId, TriageCompanyStatus status,
                                                          Pageable pageable);

    Page<TriageCompany> findByProjectIdAndStatusAndCompanyNameContainingIgnoreCase(
            UUID projectId, TriageCompanyStatus status, String companyName, Pageable pageable);

    /**
     * {@code matchingIds} comes from {@code MappedExecutiveLookup} (no join to candidates) and is never
     * empty — the caller short-circuits rather than send {@code in ()}. A null name is no filter.
     */
    @Query("select t from TriageCompany t "
            + "where t.projectId = :projectId and t.status = :status and t.id in :matchingIds "
            + "and (:companyName is null or lower(t.companyName) like lower("
            + "  concat('%', cast(:companyName as string), '%')))")
    Page<TriageCompany> findByProjectIdAndStatusAndIdInAndCompanyNameFilter(
            @Param("projectId") UUID projectId, @Param("status") TriageCompanyStatus status,
            @Param("matchingIds") Set<UUID> matchingIds, @Param("companyName") String companyName,
            Pageable pageable);

    Optional<TriageCompany> findByIdAndProjectId(UUID id, UUID projectId);

    default TriageCompany requireInProject(UUID id, UUID projectId) {
        return findByIdAndProjectId(id, projectId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    long countByProjectIdAndStatus(UUID projectId, TriageCompanyStatus status);

    /** One query for the projects list; a mandate holding nothing is absent rather than zero. */
    @Query("select new app.lightmove.api.triagecompany.model.TriageCompanyCount(c.projectId, count(c)) "
            + "from TriageCompany c where c.projectId in :projectIds and c.status <> :excludedStatus "
            + "group by c.projectId")
    List<TriageCompanyCount> countByProjectIdInExcludingStatus(Collection<UUID> projectIds,
                                                               TriageCompanyStatus excludedStatus);

    Optional<TriageCompany> findByProjectIdAndApolloAccountId(UUID projectId, String apolloAccountId);

    /**
     * The capture guard, wider than V34's manual-only index. {@code exists} (and a list below), never a
     * single-result finder: Apollo can carry two accounts under one name, and the second row would
     * throw {@code IncorrectResultSizeDataAccessException}, turning the 409 into a 500.
     */
    boolean existsByProjectIdAndCompanyNameIgnoreCase(UUID projectId, String companyName);

    List<TriageCompany> findByProjectIdAndCompanyNameIgnoreCase(UUID projectId, String companyName);
}
