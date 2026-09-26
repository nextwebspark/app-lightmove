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
     * The Companies grid's Executive-name or Status-checkbox filter, either or both — narrowed to a
     * caller-supplied set of company ids rather than a join or an {@code exists} against
     * {@code Candidate}: {@code triagecompany} does not know about candidates, by the same rule
     * {@link app.lightmove.api.candidate.model.Candidate} states from its own side, so the id set is
     * computed on the other side of that boundary, through {@code MappedExecutiveLookup}, and handed
     * in here already resolved.
     *
     * <p>{@code matchingIds} is never empty: the caller short-circuits to an empty page itself once an
     * empty set proves no company can qualify, rather than asking Postgres to evaluate {@code in ()}.
     * {@code companyName} may still be blank, which {@code is null or} treats as "no opinion" the same
     * way the plain listing below does.
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

    /**
     * The same count for many mandates at once, so the projects list stays one query rather than one
     * per row. Grouped, so a mandate holding nothing is missing from the result rather than zero.
     */
    @Query("select new app.lightmove.api.triagecompany.model.TriageCompanyCount(c.projectId, count(c)) "
            + "from TriageCompany c where c.projectId in :projectIds and c.status <> :excludedStatus "
            + "group by c.projectId")
    List<TriageCompanyCount> countByProjectIdInExcludingStatus(Collection<UUID> projectIds,
                                                               TriageCompanyStatus excludedStatus);

    Optional<TriageCompany> findByProjectIdAndApolloAccountId(UUID projectId, String apolloAccountId);

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
