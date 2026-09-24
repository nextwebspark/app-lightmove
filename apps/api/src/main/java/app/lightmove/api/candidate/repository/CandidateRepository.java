package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * A mandate's mapped executives. Every finder carries the project id — a candidate is mandate content,
 * and an unscoped lookup on people the firm is researching must not exist. The project itself is
 * resolved against the caller's workspace one layer up.
 *
 * <p>The three list finders differ only in which company filter they apply, and all three take the
 * search box's text through {@code FullNameContainingIgnoreCase}. A blank search is not special-cased
 * because it does not need to be: {@code full_name} is NOT NULL, so {@code LIKE '%%'} matches every
 * row.
 */
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    Page<Candidate> findByProjectIdAndFullNameContainingIgnoreCase(
            UUID projectId, String fullName, Pageable pageable);

    /** The talent map's read: the whole mandate, with no search box above it to narrow. */
    Page<Candidate> findByProjectId(UUID projectId, Pageable pageable);

    /** The Companies grid's read: the people at the companies on the page being rendered. */
    Page<Candidate> findByProjectIdAndTriageCompanyIdInAndFullNameContainingIgnoreCase(
            UUID projectId, Collection<UUID> triageCompanyIds, String fullName, Pageable pageable);

    /** The rest — executives whose employer is not one of the mandate's triaged companies. */
    Page<Candidate> findByProjectIdAndTriageCompanyIdIsNullAndFullNameContainingIgnoreCase(
            UUID projectId, String fullName, Pageable pageable);

    Optional<Candidate> findByIdAndProjectId(UUID id, UUID projectId);

    /**
     * The projects list's "Candidates" number, for every mandate on the page at once so the list stays
     * one query rather than one per row. Grouped, so a mandate with nobody mapped is missing from the
     * result rather than zero. Which statuses the caller leaves out is the caller's policy.
     */
    @Query("select new app.lightmove.api.candidate.model.CandidateCount(c.projectId, count(c)) "
            + "from Candidate c where c.projectId in :projectIds "
            + "and c.status not in :excludedStatuses group by c.projectId")
    List<CandidateCount> countByProjectIdInExcludingStatuses(Collection<UUID> projectIds,
                                                             Collection<CandidateStatus> excludedStatuses);

    @Query("select new app.lightmove.api.candidate.model.CandidateCount(c.projectId, count(c)) "
            + "from Candidate c where c.projectId in :projectIds "
            + "and c.status in :statuses group by c.projectId")
    List<CandidateCount> countByProjectIdInAndStatusIn(Collection<UUID> projectIds,
                                                       Collection<CandidateStatus> statuses);

    /**
     * Per mandate, how many of its non-declined triaged companies have at least one executive mapped
     * at them, as {@code [projectId, count]} rows. Native for the reason
     * {@link #findTriageCompanyIdsRankedByExecutiveStatus} is: the declined filter lives on
     * {@code triagecompany}'s table, which this side may join but not reach through an entity.
     */
    @Query(
            value = "select c.project_id, count(distinct c.triage_company_id) "
                    + "from app_lm_project_candidate c "
                    + "join app_lm_project_triage_company t on t.id = c.triage_company_id "
                    + "where c.project_id in (:projectIds) and t.status <> 'DECLINED' "
                    + "group by c.project_id",
            nativeQuery = true)
    List<Object[]> countMappedCompaniesByProjectIdIn(Collection<UUID> projectIds);

    boolean existsByIdAndProjectId(UUID id, UUID projectId);

    /**
     * The duplicate guard for someone mapped at one of the mandate's companies, and its partner below
     * for someone who is not — V36's two partial unique indexes draw the same line in the schema.
     *
     * <p>The company-scoped one carries the project id too, though a company already belongs to
     * exactly one project. Without it the guard is scoped only by whatever proved the company first,
     * which is a property of the calling order rather than of this query.
     *
     * <p>Lists rather than {@code exists}, so an edit can exclude the row being edited. A list rather
     * than {@code Optional} because nothing stops two rows sharing a name, and a single-result finder
     * would turn the 409 this guard raises into a 500.
     */
    List<Candidate> findByProjectIdAndTriageCompanyIdAndFullNameIgnoreCase(
            UUID projectId, UUID triageCompanyId, String fullName);

    /**
     * How an import recognises someone it has already mapped. Email first: it identifies a person
     * rather than describing them, and survives two exports spelling the name differently. Any of
     * the addresses the ledger holds for them counts, matched on the key the ledger dedupes by. A
     * list rather than {@code Optional} because nothing stops two rows carrying one address.
     */
    @Query("""
            select distinct c from Candidate c join c.contacts k
            where c.projectId = :projectId and k.channel = 'EMAIL' and k.valueKey = :emailKey
            """)
    List<Candidate> findByProjectIdAndEmailKey(UUID projectId, String emailKey);

    List<Candidate> findByProjectIdAndTriageCompanyIdIsNullAndFullNameIgnoreCase(
            UUID projectId, String fullName);

    /**
     * The mandate's rows that might name one LinkedIn profile — the narrowing half of the duplicate
     * guard the name finders above cannot answer. {@code like} rather than equality because the column
     * holds whatever the plugin read off the page, and one profile is written several ways: a trailing
     * slash, a locale prefix, {@code www} or not.
     *
     * <p>It narrows rather than decides. A slug that is a prefix of another matches here — {@code john}
     * against {@code /in/johnny} — so the caller settles identity on the slug itself, which is the only
     * spelling {@code LinkedInUrls} treats as the profile's name.
     */
    @Query("select c from Candidate c where c.projectId = :projectId "
            + "and lower(c.linkedinUrl) like concat('%/in/', :slug, '%')")
    List<Candidate> findByProjectIdAndProfileSlugLike(UUID projectId, String slug);

    /**
     * Ids of the mandate's triaged companies with a mapped executive whose name matches — the seam
     * behind {@code MappedExecutiveLookupAdapter}'s answer to {@code triagecompany}'s Executive-name
     * filter. Distinct, and {@code triageCompanyId is not null} rather than trusting the caller never
     * to pass one of this project's unmapped executives through: a company id set has no place for
     * {@code null} in it.
     */
    @Query("select distinct c.triageCompanyId from Candidate c "
            + "where c.projectId = :projectId and c.triageCompanyId is not null "
            + "and lower(c.fullName) like lower(concat('%', cast(:executiveName as string), '%'))")
    Set<UUID> findTriageCompanyIdsByProjectIdAndFullNameContainingIgnoreCase(
            UUID projectId, String executiveName);

    /**
     * Ids of the mandate's triaged companies with a mapped executive in one of these statuses — the
     * seam behind {@code MappedExecutiveLookupAdapter}'s answer to {@code triagecompany}'s Status
     * column filter.
     */
    @Query("select distinct c.triageCompanyId from Candidate c "
            + "where c.projectId = :projectId and c.triageCompanyId is not null and c.status in :statuses")
    Set<UUID> findTriageCompanyIdsByProjectIdAndStatusIn(UUID projectId, Collection<CandidateStatus> statuses);

    /**
     * A page of one project's stage, ranked by each triaged company's best mapped executive status —
     * the join {@code triagecompany}'s own repository is not allowed to make itself, since reaching
     * into {@code app_lm_project_candidate} from that side is exactly the reverse dependency the two
     * packages document against. {@code candidate} may reference {@code app_lm_project_triage_company}
     * directly — the one direction the dependency runs — so the query, and the join it needs, live
     * here instead, behind {@code MappedExecutiveLookupAdapter}.
     *
     * <p>Selects the id alone, never a {@code TriageCompany} row: the boundary this exists to keep is
     * narrower than "may reference the table", and returning entities of another module's aggregate
     * from here would widen it further than the join itself needs to.
     *
     * <p>{@code ascending} folds the two directions this used to need as separate queries into the sign
     * of the ranked value itself — {@code null} stays {@code null} under negation, so {@code nulls last}
     * keeps an unranked company last regardless of which way the multiplier flips everyone else.
     *
     * <p>The {@code CASE} matches {@link app.lightmove.api.candidate.constant.CandidateStatus}'s
     * declared order exactly, and its stored enum names, per that entity's own {@code @Enumerated(STRING)}.
     * Native rather than JPQL: {@code group by t.id} relies on Postgres's rule that grouping by a
     * primary key lets every other column of that row through ungrouped, which is a Postgres extension
     * JPQL does not model. The same literal spelling is mirrored in
     * {@code TriageCompanyService.EXECUTIVE_STATUS_TOKENS} and
     * {@code MappedExecutiveLookupAdapter#triageCompanyIdsWithExecutiveStatusIn} — {@code @Query} needs
     * a compile-time constant, so none of the three can reference the enum directly, and a rename has
     * to update all three by hand. {@code CandidateRepositoryStatusOrderTest} pins this one against the
     * enum so a forgotten update is a red build rather than a silent mis-rank.
     */
    @Query(
            value = "select t.id from app_lm_project_triage_company t "
                    + "left join app_lm_project_candidate c on c.triage_company_id = t.id "
                    + "where t.project_id = :projectId and t.status = :status "
                    + "and (:companyName is null or lower(t.company_name) like lower(concat('%', :companyName, '%'))) "
                    + "and (:executiveName is null or exists ("
                    + "  select 1 from app_lm_project_candidate x where x.triage_company_id = t.id "
                    + "  and lower(x.full_name) like lower(concat('%', :executiveName, '%'))"
                    + ")) "
                    + "group by t.id "
                    + "order by (case when :ascending then 1 else -1 end) * min(case c.status "
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
    Page<UUID> findTriageCompanyIdsRankedByExecutiveStatus(
            UUID projectId, String status, String companyName, String executiveName,
            boolean ascending, Pageable pageable);

    /** {@link #findTriageCompanyIdsRankedByExecutiveStatus} narrowed to companies with at least one
     *  mapped executive whose status is one of the ticked ones — its own method for the reason
     *  {@code TriageCompanyRepository}'s equivalent used to be: the caller only ever reaches this one
     *  with a non-empty {@code executiveStatuses}, so the {@code in} needs no null-guard the way the
     *  other two filters above do. */
    @Query(
            value = "select t.id from app_lm_project_triage_company t "
                    + "left join app_lm_project_candidate c on c.triage_company_id = t.id "
                    + "where t.project_id = :projectId and t.status = :status "
                    + "and (:companyName is null or lower(t.company_name) like lower(concat('%', :companyName, '%'))) "
                    + "and (:executiveName is null or exists ("
                    + "  select 1 from app_lm_project_candidate x where x.triage_company_id = t.id "
                    + "  and lower(x.full_name) like lower(concat('%', :executiveName, '%'))"
                    + ")) "
                    + "and exists ("
                    + "  select 1 from app_lm_project_candidate y where y.triage_company_id = t.id "
                    + "  and y.status in (:executiveStatuses)"
                    + ") "
                    + "group by t.id "
                    + "order by (case when :ascending then 1 else -1 end) * min(case c.status "
                    + "  when 'IDENTIFIED' then 0 when 'CONTACTED' then 1 when 'ENGAGED' then 2 "
                    + "  when 'INTERESTED' then 3 when 'NOT_INTERESTED' then 4 when 'OFF_LIMITS' then 5 "
                    + "  when 'OUT_OF_SCOPE' then 6 else null end) asc nulls last, t.created_at desc",
            countQuery = "select count(*) from app_lm_project_triage_company t "
                    + "where t.project_id = :projectId and t.status = :status "
                    + "and (:companyName is null or lower(t.company_name) like lower(concat('%', :companyName, '%'))) "
                    + "and (:executiveName is null or exists ("
                    + "  select 1 from app_lm_project_candidate x where x.triage_company_id = t.id "
                    + "  and lower(x.full_name) like lower(concat('%', :executiveName, '%'))"
                    + ")) "
                    + "and exists ("
                    + "  select 1 from app_lm_project_candidate y where y.triage_company_id = t.id "
                    + "  and y.status in (:executiveStatuses)"
                    + ")",
            nativeQuery = true)
    Page<UUID> findTriageCompanyIdsRankedByExecutiveStatusAndExecutiveStatuses(
            UUID projectId, String status, String companyName, String executiveName,
            List<String> executiveStatuses, boolean ascending, Pageable pageable);
}
