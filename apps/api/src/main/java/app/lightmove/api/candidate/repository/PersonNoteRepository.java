package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.NoteViewpoint;
import app.lightmove.api.candidate.model.PersonNote;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
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

    @Query(value = """
            SELECT author_user_id AS authorUserId, project_id AS projectId
            FROM app_lm_person_note
            WHERE id = :id AND workspace_id = :workspaceId AND person_id = :personId AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    Optional<DeletedPersonNoteRow> findDeleted(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
                                               @Param("personId") UUID personId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE app_lm_person_note SET deleted_at = NULL, deleted_by = NULL, version = version + 1
            WHERE id = :id AND workspace_id = :workspaceId AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    int restore(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

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
