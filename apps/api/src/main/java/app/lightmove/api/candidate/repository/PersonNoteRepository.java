package app.lightmove.api.candidate.repository;

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

    Optional<PersonNote> findByIdAndWorkspaceIdAndPersonId(UUID id, UUID workspaceId, UUID personId);

    List<PersonNote> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    /** The same words already on this person about this mandate: a re-imported sheet adds nothing. */
    boolean existsByWorkspaceIdAndPersonIdAndProjectIdAndBody(UUID workspaceId, UUID personId, UUID projectId,
                                                              String body);

    default PersonNote requireOnPerson(UUID id, UUID workspaceId, UUID personId) {
        return findByIdAndWorkspaceIdAndPersonId(id, workspaceId, personId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
