package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.PersonDocument;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** A person's documents. Every finder carries the workspace id, for {@link PersonRepository}'s reason. */
public interface PersonDocumentRepository extends JpaRepository<PersonDocument, UUID> {

    List<PersonDocument> findByWorkspaceIdAndPersonId(UUID workspaceId, UUID personId);

    Optional<PersonDocument> findByIdAndWorkspaceIdAndPersonId(UUID id, UUID workspaceId, UUID personId);

    List<PersonDocument> findByWorkspaceIdAndPersonIdAndNameKey(UUID workspaceId, UUID personId, String nameKey);

    Optional<PersonDocument> findByWorkspaceIdAndPersonIdAndPrimaryCvTrue(UUID workspaceId, UUID personId);

    List<PersonDocument> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    long countByWorkspaceIdAndPersonId(UUID workspaceId, UUID personId);

    default PersonDocument requireOnPerson(UUID id, UUID workspaceId, UUID personId) {
        return findByIdAndWorkspaceIdAndPersonId(id, workspaceId, personId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
