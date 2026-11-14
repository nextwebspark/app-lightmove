package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.model.LoggedContactRow;
import app.lightmove.api.candidate.model.NoteViewpoint;
import app.lightmove.api.candidate.model.PersonNote;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * A person's notes. Every finder carries the workspace id, for {@link PersonRepository}'s reason. The derived
 * finders see live notes only ({@code PersonNote}'s restriction); the two native queries are the only way to a
 * deleted one.
 */
public interface PersonNoteRepository extends JpaRepository<PersonNote, UUID> {

    List<PersonNote> findByWorkspaceIdAndPersonIdOrderByPinnedDescCreatedAtDesc(UUID workspaceId, UUID personId);

    List<PersonNote> findByWorkspaceIdAndPersonIdOrderByCreatedAtDesc(UUID workspaceId, UUID personId);

    Optional<PersonNote> findByIdAndWorkspaceIdAndPersonId(UUID id, UUID workspaceId, UUID personId);

    List<PersonNote> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    /** The same words already on this person about this mandate: a re-imported sheet adds nothing. */
    boolean existsByWorkspaceIdAndPersonIdAndProjectIdAndBody(UUID workspaceId, UUID personId, UUID projectId,
                                                              String body);

    /**
     * A live note by the same author in the same words about the same position: the copy a door filed after the
     * original was deleted. Another author's note in the same words is theirs, never a stand-in for this one.
     */
    Optional<PersonNote> findFirstByWorkspaceIdAndPersonIdAndProjectIdAndBodyAndAuthorUserId(UUID workspaceId,
            UUID personId, UUID projectId, String body, UUID authorUserId);

    @Query(value = """
            SELECT author_user_id AS authorUserId, project_id AS projectId, body
            FROM app_lm_person_note
            WHERE id = :id AND workspace_id = :workspaceId AND person_id = :personId AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    Optional<DeletedPersonNoteRow> findDeleted(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
                                               @Param("personId") UUID personId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE app_lm_person_note SET deleted_at = NULL, deleted_by = NULL, version = version + 1
            WHERE id = :id AND workspace_id = :workspaceId AND person_id = :personId AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    int restore(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId, @Param("personId") UUID personId);

    /** Every live logged touch with these people since {@code since}, on any position, without its words. */
    @Query("""
            select n.personId as personId, n.projectId as projectId, n.authorUserId as authorUserId, n.kind as kind,
                   coalesce(n.occurredAt, n.createdAt) as at
            from PersonNote n
            where n.workspaceId = :workspaceId and n.personId in :personIds and n.kind in :kinds
              and coalesce(n.occurredAt, n.createdAt) >= :since
            """)
    List<LoggedContactRow> findLoggedContacts(UUID workspaceId, Collection<UUID> personIds,
                                              Collection<PersonNoteKind> kinds, Instant since);

    /** The same read before {@code since}: each person's newest logged touch per teammate and position. */
    @Query(value = """
            SELECT DISTINCT ON (person_id, author_user_id, project_id) * FROM app_lm_person_note
            WHERE workspace_id = :workspaceId AND person_id IN (:personIds) AND kind IN (:kinds)
              AND deleted_at IS NULL AND coalesce(occurred_at, created_at) < :since
            ORDER BY person_id, author_user_id, project_id, coalesce(occurred_at, created_at) DESC
            """, nativeQuery = true)
    List<PersonNote> findLatestLoggedContactsBefore(UUID workspaceId, Collection<UUID> personIds,
                                                    Collection<String> kinds, Instant since);

    /** The notes a viewpoint reads, pinned first and then newest first. */
    default List<PersonNote> findReadablePinnedFirst(UUID workspaceId, UUID personId, NoteViewpoint viewpoint) {
        return readableOf(findByWorkspaceIdAndPersonIdOrderByPinnedDescCreatedAtDesc(workspaceId, personId), viewpoint);
    }

    default List<PersonNote> findReadableNewestFirst(UUID workspaceId, UUID personId, NoteViewpoint viewpoint) {
        return readableOf(findByWorkspaceIdAndPersonIdOrderByCreatedAtDesc(workspaceId, personId), viewpoint);
    }

    default List<PersonNote> findReadableIn(UUID workspaceId, Collection<UUID> ids, NoteViewpoint viewpoint) {
        return readableOf(findByWorkspaceIdAndIdIn(workspaceId, ids), viewpoint);
    }

    /** A note another position holds is not found from this one, as if it were not there. */
    default PersonNote requireReadable(UUID id, UUID workspaceId, UUID personId, NoteViewpoint viewpoint) {
        return findByIdAndWorkspaceIdAndPersonId(id, workspaceId, personId)
                .filter(note -> note.isReadableFrom(viewpoint))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private static List<PersonNote> readableOf(List<PersonNote> notes, NoteViewpoint viewpoint) {
        return notes.stream().filter(note -> note.isReadableFrom(viewpoint)).toList();
    }
}
