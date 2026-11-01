package app.lightmove.api.triagecompany.repository;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.TriageCompany;
import app.lightmove.api.triagecompany.model.TriageCompanyCount;
import app.lightmove.api.triagecompany.model.TriageCompanyLogo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
     * A stage's own companies plus {@code movedToStageIds}, those listed here for a person of theirs moved on their
     * own (#903) — never empty, as above. A null name is no filter.
     */
    @Query("select t from TriageCompany t "
            + "where t.projectId = :projectId and (t.status = :status or t.id in :movedToStageIds) "
            + "and (:companyName is null or lower(t.companyName) like lower("
            + "  concat('%', cast(:companyName as string), '%')))")
    Page<TriageCompany> findByProjectIdAndStatusOrIdInAndCompanyNameFilter(
            @Param("projectId") UUID projectId, @Param("status") TriageCompanyStatus status,
            @Param("movedToStageIds") Set<UUID> movedToStageIds, @Param("companyName") String companyName,
            Pageable pageable);

    /** {@code ids} already holds the stage (each has an executive listed there); never empty. */
    @Query("select t from TriageCompany t "
            + "where t.projectId = :projectId and t.id in :ids "
            + "and (:companyName is null or lower(t.companyName) like lower("
            + "  concat('%', cast(:companyName as string), '%')))")
    Page<TriageCompany> findByProjectIdAndIdInAndCompanyNameFilter(
            @Param("projectId") UUID projectId, @Param("ids") Set<UUID> ids,
            @Param("companyName") String companyName, Pageable pageable);

    Page<TriageCompany> findByProjectIdAndCompanyNameContainingIgnoreCase(
            UUID projectId, String companyName, Pageable pageable);

    /**
     * The company half of the mandate's one search rule: its name, city, country or industry, and — only for
     * {@code includeStaffContent}, a reader holding {@code WORK_EXECUTE} — its live notes. {@code pattern} is
     * {@code SearchText.likePattern}. The executive half is {@code candidate}'s, which this side never reads.
     */
    @Query(value = "select t.id as \"id\", t.status as \"status\" from app_lm_project_triage_company t "
            + "where t.project_id = :projectId and ("
            + "  lower(t.company_name) like :pattern escape '\\' "
            + "  or lower(coalesce(t.company_city, '')) like :pattern escape '\\' "
            + "  or lower(coalesce(t.company_country, '')) like :pattern escape '\\' "
            + "  or lower(coalesce(t.industry, '')) like :pattern escape '\\' "
            + "  or (:includeStaffContent and exists ("
            + "    select 1 from app_lm_company_note n where n.triage_company_id = t.id and n.deleted_at is null "
            + "    and lower(n.body) like :pattern escape '\\')))", nativeQuery = true)
    List<SearchMatchedCompany> findSearchMatches(@Param("projectId") UUID projectId, @Param("pattern") String pattern,
                                                 @Param("includeStaffContent") boolean includeStaffContent);

    Page<TriageCompany> findByProjectIdAndStatusAndIdNotIn(
            UUID projectId, TriageCompanyStatus status, Collection<UUID> excludedIds, Pageable pageable);

    Optional<TriageCompany> findByIdAndProjectId(UUID id, UUID projectId);

    List<TriageCompany> findByProjectIdAndStatusAndIdIn(UUID projectId, TriageCompanyStatus status,
                                                         Collection<UUID> ids);

    List<TriageCompany> findByProjectIdAndStatusAndNoExecutiveFoundFalseAndIdNotIn(
            UUID projectId, TriageCompanyStatus status, Collection<UUID> excludedIds, Sort sort, Limit limit);

    default TriageCompany requireInProject(UUID id, UUID projectId) {
        return findByIdAndProjectId(id, projectId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /**
     * {@code FOR UPDATE}, which a candidate row's foreign-key check (a key-share lock) waits for, so nobody is mapped
     * there between a removal's count and its delete. Not {@code @Lock(PESSIMISTIC_WRITE)}: Hibernate takes
     * {@code FOR NO KEY UPDATE} for it on Postgres, which that check does not wait for, and a removal stranded someone.
     */
    @Query(value = "SELECT * FROM app_lm_project_triage_company WHERE id = :id AND project_id = :projectId FOR UPDATE",
            nativeQuery = true)
    Optional<TriageCompany> findForUpdate(@Param("id") UUID id, @Param("projectId") UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, TriageCompanyStatus status);

    @Query("select t.id from TriageCompany t where t.projectId = :projectId and t.status = :status")
    Set<UUID> findIdsByProjectIdAndStatus(UUID projectId, TriageCompanyStatus status);

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

    /** Narrows to the rows whose LinkedIn page may be {@code slug}'s; a prefix matches too, so the caller settles it. */
    @Query("select t from TriageCompany t where t.projectId = :projectId "
            + "and lower(t.companyLinkedinUrl) like concat('%/company/', :slug, '%')")
    List<TriageCompany> findByProjectIdAndCompanySlugLike(UUID projectId, String slug);

    List<TriageCompany> findByProjectIdAndApolloAccountIdIn(UUID projectId, Collection<String> apolloAccountIds);

    @Query("select t from TriageCompany t "
            + "where t.projectId = :projectId and lower(t.companyName) in :lowerCaseNames")
    List<TriageCompany> findByProjectIdAndLowerCaseCompanyNameIn(
            @Param("projectId") UUID projectId, @Param("lowerCaseNames") Collection<String> lowerCaseNames);

    @Query("select new app.lightmove.api.triagecompany.model.TriageCompanyLogo(t.id, t.projectId, t.logoUrl) "
            + "from TriageCompany t where t.id in :ids and t.logoUrl is not null")
    List<TriageCompanyLogo> findLogosByIdIn(@Param("ids") Collection<UUID> ids);
}
