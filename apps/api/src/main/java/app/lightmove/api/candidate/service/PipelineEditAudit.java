package app.lightmove.api.candidate.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Every admin edit of the pipeline is one audit event: a {@code pipeline} section of the workspace. */
@Component
@RequiredArgsConstructor
class PipelineEditAudit {

    private final AuditService audit;

    void record(UUID userId, UUID workspaceId, String change, UUID targetId, HttpServletRequest httpRequest) {
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED).actor(userId).workspace(workspaceId).from(httpRequest)
                .detail("section", "pipeline")
                .detail("change", change)
                .detailIfPresent("targetId", targetId == null ? null : targetId.toString())
                .record();
    }
}
