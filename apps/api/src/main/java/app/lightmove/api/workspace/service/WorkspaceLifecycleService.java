package app.lightmove.api.workspace.service;

import app.lightmove.api.billing.plan.service.WorkspaceBillingClosure;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.model.WorkspacePurgeCertificate;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uncava's side of a deleted workspace: bringing it back within its grace period, and reading the certificate its
 * purge left. Every member is gone by then, so neither is anyone's in the workspace; the caller is a platform admin
 * and runs this inside the workspace's scope.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceLifecycleService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final WorkspacePurgeCertificates certificates;
    private final WorkspaceBillingClosure billing;
    private final AuditService audit;

    /**
     * The workspace and the memberships its deletion ended come back. What the deletion let go at a provider — mailboxes,
     * Zoom, API keys, AI apps — does not, and each member connects again; pending invitations stay revoked.
     */
    @Transactional
    public int restore(UUID actorId, UUID workspaceId, HttpServletRequest request) {
        Workspace workspace = workspaces.lockById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
        workspace.restore();
        List<WorkspaceMember> ended = members.findByWorkspaceIdAndEndedWithWorkspaceTrue(workspaceId);
        ended.forEach(WorkspaceMember::restoreWithWorkspace);
        billing.onWorkspaceRestored(workspaceId);
        audit.event(WorkspaceEventType.WORKSPACE_RESTORED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("members", ended.size())
                .record();
        return ended.size();
    }

    @Transactional(readOnly = true)
    public WorkspacePurgeCertificate certificate(UUID workspaceId) {
        return certificates.of(workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
    }
}
