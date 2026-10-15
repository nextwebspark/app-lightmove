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

/** A person's notes. Every finder carries the workspace id, for {@link PersonRepository}'s reason. */
public interface PersonNoteRepository extends JpaRepository<PersonNote, UUID> {

    List<PersonNote> findByWorkspaceIdAndPersonIdOrderByPinnedDescCreatedAtDesc(UUID workspaceId, UUID personId);

    List<PersonNote> findByWorkspaceIdAndPersonIdOrderByCreatedAtDesc(UUID workspaceId, UUID personId);

    Optional<PersonNote> findByIdAndWorkspaceIdAndPersonId(UUID id, UUID workspaceId, UUID personId);

    List<PersonNote> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    /** The same words already on this person about this mandate: a re-imported sheet adds nothing. */
    boolean existsByWorkspaceIdAndPersonIdAndProjectIdAndBody(UUID workspaceId, UUID personId, UUID projectId,
                                                              String body);

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
