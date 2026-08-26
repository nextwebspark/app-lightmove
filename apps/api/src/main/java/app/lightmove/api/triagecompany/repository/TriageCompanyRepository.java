package app.lightmove.api.triagecompany.repository;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.TriageCompany;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Optional<TriageCompany> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, TriageCompanyStatus status);

    Optional<TriageCompany> findByProjectIdAndApolloAccountId(UUID projectId, String apolloAccountId);

    /**
     * The duplicate guard behind a capture, and wider than the partial unique index V34 adds: that
     * index can only see the manual rows, so a company typed in under a name the mandate already took
     * out of Apollo would pass it. Matching across every source is also what a consultant means by
     * "already there" — where the row came from is not the question they are asking.
     *
     * <p>{@code exists}, not a finder: the name is not unique within a project and cannot be made so.
     * Nothing stops the Apollo export carrying two accounts under one name, and a bulk add takes both;
     * a single-result finder over that column throws {@code IncorrectResultSizeDataAccessException}
     * the moment it meets the second row, turning the 409 this guard exists to raise into a 500.
     */
    boolean existsByProjectIdAndCompanyNameIgnoreCase(UUID projectId, String companyName);
}
