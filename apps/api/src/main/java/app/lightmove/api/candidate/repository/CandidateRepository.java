package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.Candidate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

    /** The Companies grid's read: the people at the companies on the page being rendered. */
    Page<Candidate> findByProjectIdAndTriageCompanyIdInAndFullNameContainingIgnoreCase(
            UUID projectId, Collection<UUID> triageCompanyIds, String fullName, Pageable pageable);

    /** The rest — executives whose employer is not one of the mandate's triaged companies. */
    Page<Candidate> findByProjectIdAndTriageCompanyIdIsNullAndFullNameContainingIgnoreCase(
            UUID projectId, String fullName, Pageable pageable);

    Optional<Candidate> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByIdAndProjectId(UUID id, UUID projectId);

    /**
     * The duplicate guard for someone mapped at one of the mandate's companies, and its partner below
     * for someone who is not. Two questions rather than one, because "the same person twice" means
     * different things on either side of the mapping — V36's two partial unique indexes draw the same
     * line in the schema.
     *
     * <p>The company-scoped one carries the project id too, even though a company already belongs to
     * exactly one project. Without it the guard is scoped only by whatever proved the company first,
     * which is a property of the calling order rather than of this query — and the class doc above
     * promises a reader something stronger than that.
     *
     * <p>Lists rather than {@code exists}, so an edit can exclude the row being edited: renaming
     * someone must not collide with themselves. A list rather than {@code Optional} for the reason the
     * triage repository spells out — nothing stops two rows sharing a name if an index was added after
     * the data, and a single-result finder would turn the 409 this guard exists to raise into a 500.
     */
    List<Candidate> findByProjectIdAndTriageCompanyIdAndFullNameIgnoreCase(
            UUID projectId, UUID triageCompanyId, String fullName);

    /**
     * How an import recognises someone it has already mapped. Email first, because it is the one field
     * on an executive's row that identifies a person rather than describing them — a spreadsheet
     * spells a name three ways across two exports and the address stays the same.
     *
     * <p>A list rather than {@code Optional} for the same reason the name finders below are: nothing
     * stops two rows carrying one address, and a single-result finder would turn that into a 500.
     */
    List<Candidate> findByProjectIdAndEmailIgnoreCase(UUID projectId, String email);

    List<Candidate> findByProjectIdAndTriageCompanyIdIsNullAndFullNameIgnoreCase(
            UUID projectId, String fullName);
}
