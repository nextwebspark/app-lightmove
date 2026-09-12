package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
     * result rather than zero.
     */
    @Query("select new app.lightmove.api.candidate.model.CandidateCount(c.projectId, count(c)) "
            + "from Candidate c where c.projectId in :projectIds group by c.projectId")
    List<CandidateCount> countByProjectIdIn(Collection<UUID> projectIds);

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
     * rather than describing them, and survives two exports spelling the name differently. A list
     * rather than {@code Optional} because nothing stops two rows carrying one address.
     */
    List<Candidate> findByProjectIdAndEmailIgnoreCase(UUID projectId, String email);

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
}
