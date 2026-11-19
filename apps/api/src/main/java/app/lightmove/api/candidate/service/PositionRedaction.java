package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.PositionVisibility;
import app.lightmove.api.project.service.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Which of a person's other positions a caller may see named. A client seat names nothing; an admin, everything. */
@Service
@RequiredArgsConstructor
public class PositionRedaction {

    private final ProjectAccess projectAccess;

    public PositionVisibility visibleTo(UUID callerId, UUID workspaceId, Collection<UUID> projectIds,
                                        UUID currentProjectId) {
        return new PositionVisibility(currentProjectId, projectAccess.projectsWithAction(callerId, workspaceId,
                projectIds, ProjectAction.WORK_EXECUTE));
    }
}
