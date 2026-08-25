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
     * The duplicate guard behind a capture, and wider than the partial unique index V33 adds: that
     * index can only see the manual rows, so a company typed in under a name the mandate already took
     * out of Apollo would pass it. Matching across every source is also what a consultant means by
     * "already there" — where the row came from is not the question they are asking.
     */
    Optional<TriageCompany> findByProjectIdAndCompanyNameIgnoreCase(UUID projectId, String companyName);
}
