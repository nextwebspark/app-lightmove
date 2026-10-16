package app.uncava.api.outreach.repository;

import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.outreach.model.OutreachSequence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutreachSequenceRepository extends JpaRepository<OutreachSequence, UUID> {

    List<OutreachSequence> findByWorkspaceIdAndProjectIdOrderByCreatedAtAsc(UUID workspaceId, UUID projectId);

    Optional<OutreachSequence> findByIdAndWorkspaceIdAndProjectId(UUID id, UUID workspaceId, UUID projectId);

    default OutreachSequence requireInProject(UUID id, UUID workspaceId, UUID projectId) {
        return findByIdAndWorkspaceIdAndProjectId(id, workspaceId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
