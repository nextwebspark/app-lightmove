package app.lightmove.api.triagecompany.repository;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.TriageCompany;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * A mandate's triaged companies. Every finder carries the project id — the universe is mandate
 * content, and an unscoped lookup on it must not exist. The project itself is resolved against the
 * caller's workspace one layer up.
 */
public interface TriageCompanyRepository extends JpaRepository<TriageCompany, UUID> {

    Page<TriageCompany> findByProjectIdAndStatus(UUID projectId, TriageCompanyStatus status,
                                                          Pageable pageable);

    /** The grid's search box. Substring rather than prefix: "emirates" should find "Bank of Emirates". */
    Page<TriageCompany> findByProjectIdAndStatusAndCompanyNameContainingIgnoreCase(
            UUID projectId, TriageCompanyStatus status, String companyName, Pageable pageable);

    /**
     * Both halves of the Companies grid's own search — company name and, independently, a mapped
     * executive's — narrowed to the companies that actually have one. Either filter may be blank,
     * which the {@code is null or} branch treats as "no opinion" rather than "matches nothing"; a
     * blank string is normalised to {@code null} by the caller before this runs.
     *
     * <p>An {@code exists} subquery rather than a join: {@code TriageCompany} carries no JPA
     * association to {@code Candidate} — {@code triagecompany} does not know about candidates, by the
     * same rule {@link app.lightmove.api.candidate.model.Candidate} states from its own side — and a
     * join would multiply a company row once per matching executive, which a plain {@code exists}
     * never does. Spring Data derives the count query from this one without help, since neither
     * filter changes how many rows a company can match.
     *
     * <p>{@code cast(... as string)}: a parameter that is only ever compared with {@code is null} has
     * nothing in the query to infer a type from, and Postgres's JDBC driver resolves that ambiguity to
     * {@code bytea} rather than {@code text} — {@code lower(bytea)} then has no overload and every call
     * with a null filter throws. The cast pins the type before the null check is reached.
     */
    @Query("select t from TriageCompany t "
            + "where t.projectId = :projectId and t.status = :status "
            + "and (:companyName is null or lower(t.companyName) like lower("
            + "  concat('%', cast(:companyName as string), '%'))) "
            + "and (:executiveName is null or exists ("
            + "  select 1 from Candidate c where c.triageCompanyId = t.id "
            + "  and lower(c.fullName) like lower(concat('%', cast(:executiveName as string), '%'))"
            + "))")
    Page<TriageCompany> findByProjectIdAndStatusAndFilters(
            @Param("projectId") UUID projectId, @Param("status") TriageCompanyStatus status,
            @Param("companyName") String companyName, @Param("executiveName") String executiveName,
            Pageable pageable);

    /**
     * Ranks a page by its executives' status rather than by any property of the company itself, which
     * {@link app.lightmove.api.triagecompany.constant.TriageCompanySortField}'s flat JPA-property
     * contract cannot express — this is a parallel path, not an extension of it, selected by
     * {@code TriageCompanyService} only for that one wire token. Carries the same two optional filters
     * as {@link #findByProjectIdAndStatusAndFilters} — a company still needs to be found before it can
     * be ranked — but keeps them as a separate {@code exists} rather than reusing the joined {@code c}
     * below: narrowing that join to the matching executive would rank a company by the one executive
     * who matched the filter rather than by the best of everyone mapped there, which is not what
     * filtering the page is supposed to change.
     *
     * <p>Native rather than JPQL: {@code group by t.id} relies on Postgres's rule that grouping by a
     * primary key lets every other column of that row through ungrouped, which is a Postgres extension
     * JPQL does not model. The {@code CASE} matches {@link app.lightmove.api.candidate.constant.CandidateStatus}'s
     * declared order exactly — the one vocabulary this reuses rather than inventing a second — and its
     * stored enum names, per that entity's own {@code @Enumerated(STRING)}. A company with no mapped
     * executive at all has no row to rank and lands last regardless of direction: {@code NULLS LAST} on
     * both, because Postgres's own default (last for ascending, first for descending) would otherwise
     * put the least-researched companies first on a descending "furthest along" sort too.
     */
    @Query(
            value = "select t.* from app_lm_project_triage_company t "
                    + "left join app_lm_project_candidate c on c.triage_company_id = t.id "
                    + "where t.project_id = :projectId and t.status = :status "
                    + "and (:companyName is null or lower(t.company_name) like lower(concat('%', :companyName, '%'))) "
                    + "and (:executiveName is null or exists ("
                    + "  select 1 from app_lm_project_candidate x where x.triage_company_id = t.id "
                    + "  and lower(x.full_name) like lower(concat('%', :executiveName, '%'))"
                    + ")) "
                    + "group by t.id "
                    + "order by min(case c.status "
                    + "  when 'IDENTIFIED' then 0 when 'CONTACTED' then 1 when 'ENGAGED' then 2 "
                    + "  when 'INTERESTED' then 3 when 'NOT_INTERESTED' then 4 when 'OFF_LIMITS' then 5 "
                    + "  when 'OUT_OF_SCOPE' then 6 else null end) asc nulls last, t.created_at desc",
            countQuery = "select count(*) from app_lm_project_triage_company t "
                    + "where t.project_id = :projectId and t.status = :status "
                    + "and (:companyName is null or lower(t.company_name) like lower(concat('%', :companyName, '%'))) "
                    + "and (:executiveName is null or exists ("
                    + "  select 1 from app_lm_project_candidate x where x.triage_company_id = t.id "
                    + "  and lower(x.full_name) like lower(concat('%', :executiveName, '%'))"
                    + "))",
            nativeQuery = true)
    Page<TriageCompany> findByProjectIdAndStatusOrderByExecutiveStatusRankAsc(
            @Param("projectId") UUID projectId, @Param("status") String status,
            @Param("companyName") String companyName, @Param("executiveName") String executiveName,
            Pageable pageable);

    /** The descending twin of {@link #findByProjectIdAndStatusOrderByExecutiveStatusRankAsc} — see it
     *  for the rest; only the rank's own direction flips, {@code NULLS LAST} stays on both. */
    @Query(
            value = "select t.* from app_lm_project_triage_company t "
                    + "left join app_lm_project_candidate c on c.triage_company_id = t.id "
                    + "where t.project_id = :projectId and t.status = :status "
                    + "and (:companyName is null or lower(t.company_name) like lower(concat('%', :companyName, '%'))) "
                    + "and (:executiveName is null or exists ("
                    + "  select 1 from app_lm_project_candidate x where x.triage_company_id = t.id "
                    + "  and lower(x.full_name) like lower(concat('%', :executiveName, '%'))"
                    + ")) "
                    + "group by t.id "
                    + "order by min(case c.status "
                    + "  when 'IDENTIFIED' then 0 when 'CONTACTED' then 1 when 'ENGAGED' then 2 "
                    + "  when 'INTERESTED' then 3 when 'NOT_INTERESTED' then 4 when 'OFF_LIMITS' then 5 "
                    + "  when 'OUT_OF_SCOPE' then 6 else null end) desc nulls last, t.created_at desc",
            countQuery = "select count(*) from app_lm_project_triage_company t "
                    + "where t.project_id = :projectId and t.status = :status "
                    + "and (:companyName is null or lower(t.company_name) like lower(concat('%', :companyName, '%'))) "
                    + "and (:executiveName is null or exists ("
                    + "  select 1 from app_lm_project_candidate x where x.triage_company_id = t.id "
                    + "  and lower(x.full_name) like lower(concat('%', :executiveName, '%'))"
                    + "))",
            nativeQuery = true)
    Page<TriageCompany> findByProjectIdAndStatusOrderByExecutiveStatusRankDesc(
            @Param("projectId") UUID projectId, @Param("status") String status,
            @Param("companyName") String companyName, @Param("executiveName") String executiveName,
            Pageable pageable);

    Optional<TriageCompany> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, TriageCompanyStatus status);

    Optional<TriageCompany> findByProjectIdAndApolloAccountId(UUID projectId, String apolloAccountId);

    /**
     * Every id this project has already taken out of the market, at any stage — what
     * {@code TriagedCompanyLookupAdapter} answers {@code strategy}'s search with, so a triaged company
     * stops reappearing in later searches. Excludes mandate-supplied rows, which never had one.
     */
    @Query("select t.apolloAccountId from TriageCompany t "
            + "where t.projectId = :projectId and t.apolloAccountId is not null")
    List<String> findApolloAccountIdsByProjectId(@Param("projectId") UUID projectId);

    /**
     * The duplicate guard behind a capture, wider than the partial unique index V34 adds: that index
     * can only see the manual rows, so a company typed in under a name already taken out of Apollo
     * would pass it.
     *
     * <p>{@code exists}, not a finder: the name is not unique within a project and cannot be made so.
     * Nothing stops the Apollo export carrying two accounts under one name, and a bulk add takes both;
     * a single-result finder over that column throws {@code IncorrectResultSizeDataAccessException}
     * the moment it meets the second row, turning the 409 this guard exists to raise into a 500.
     */
    boolean existsByProjectIdAndCompanyNameIgnoreCase(UUID projectId, String companyName);

    /**
     * The same guard for a <i>rename</i>, which has to exclude the row being renamed — saving a company
     * without changing its name must not collide with itself.
     *
     * <p>A list for the reason the finder above is an {@code exists}: the name is not unique within a
     * project and cannot be made so, because nothing stops the Apollo export carrying two accounts
     * under one name and a bulk add taking both. A single-result finder would throw
     * {@code IncorrectResultSizeDataAccessException} on the second row and turn this 409 into a 500.
     */
    List<TriageCompany> findByProjectIdAndCompanyNameIgnoreCase(UUID projectId, String companyName);
}
