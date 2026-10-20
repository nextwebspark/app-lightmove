package app.lightmove.api.triagecompany.repository;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.triagecompany.model.CompanyNote;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * A mandate's notes on its companies. Every finder carries the project id, and the derived ones see live notes only
 * ({@code CompanyNote}'s restriction); the two native queries are the only way to a deleted one.
 */
public interface CompanyNoteRepository extends JpaRepository<CompanyNote, UUID> {

    List<CompanyNote> findByProjectIdAndTriageCompanyIdOrderByPinnedDescCreatedAtDesc(UUID projectId,
                                                                                     UUID triageCompanyId);

    Optional<CompanyNote> findByIdAndProjectIdAndTriageCompanyId(UUID id, UUID projectId, UUID triageCompanyId);

    /** The same words already on this company: a re-imported sheet adds nothing. */
    boolean existsByProjectIdAndTriageCompanyIdAndBody(UUID projectId, UUID triageCompanyId, String body);

    @Query("""
            SELECT n.triageCompanyId AS triageCompanyId, COUNT(n) AS notes FROM CompanyNote n
            WHERE n.projectId = :projectId AND n.triageCompanyId IN :companyIds
            GROUP BY n.triageCompanyId""")
    List<CompanyNoteCount> countByCompany(@Param("projectId") UUID projectId,
                                          @Param("companyIds") Collection<UUID> companyIds);

    /**
     * A live note by the same author in the same words on the same company: the copy a door filed after the original
     * was deleted. Another author's note in the same words is theirs, never a stand-in for this one.
     */
    Optional<CompanyNote> findFirstByProjectIdAndTriageCompanyIdAndBodyAndAuthorUserId(UUID projectId,
            UUID triageCompanyId, String body, UUID authorUserId);

    @Query(value = """
            SELECT author_user_id AS authorUserId, body FROM app_lm_company_note
            WHERE id = :id AND project_id = :projectId AND triage_company_id = :companyId AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    Optional<DeletedCompanyNoteRow> findDeleted(@Param("id") UUID id, @Param("projectId") UUID projectId,
                                                @Param("companyId") UUID triageCompanyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE app_lm_company_note SET deleted_at = NULL, deleted_by = NULL, version = version + 1
            WHERE id = :id AND project_id = :projectId AND triage_company_id = :companyId AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    int restore(@Param("id") UUID id, @Param("projectId") UUID projectId, @Param("companyId") UUID triageCompanyId);

    default CompanyNote requireOn(UUID id, UUID projectId, UUID triageCompanyId) {
        return findByIdAndProjectIdAndTriageCompanyId(id, projectId, triageCompanyId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
