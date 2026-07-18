package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → General: read, rename/defaults, and soft deletion. Deletion flips statuses rather than
 * deleting rows — the audit trail keeps its referents, and freed members can join elsewhere.
 *
 * <p>Tier gating lives on {@code WorkspaceController} as {@code @PreAuthorize}; what stays here is
 * the typed-name confirmation and the release work itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceSettingsService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final InvitationRepository invitations;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public WorkspaceDetail get(UUID workspaceId) {
        return detail(requireWorkspace(workspaceId));
    }

    @Transactional
    public WorkspaceDetail update(UUID actorId, UUID workspaceId, String name,
                                  String defaultRegion, String defaultCurrency,
                                  HttpServletRequest request) {
        Workspace workspace = requireWorkspace(workspaceId);
        workspace.applySettings(name.trim(), defaultRegion, defaultCurrency);

        audit.event(AuditEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("name", workspace.getName())
                .record();

        return detail(workspace);
    }

    /** The typed name is verified here, not only in the browser — the server owns the guard rail. */
    @Transactional
    public void delete(UUID actorId, UUID workspaceId, String confirmName, HttpServletRequest request) {
        Workspace workspace = requireWorkspace(workspaceId);

        if (confirmName == null || !workspace.getName().equalsIgnoreCase(confirmName.trim())) {
            throw ApiException.of(ErrorCode.WORKSPACE_NAME_MISMATCH);
        }

        workspace.delete();
        members.findByWorkspaceIdAndStatus(workspaceId, MemberStatus.ACTIVE)
                .forEach(member -> member.remove());
        invitations.findByWorkspaceIdAndStatus(workspaceId, InvitationStatus.PENDING)
                .forEach(invitation -> invitation.revoke());

        log.info("User {} deleted workspace {}", actorId, workspaceId);
        audit.event(AuditEventType.WORKSPACE_DELETED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("name", workspace.getName())
                .record();
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private WorkspaceDetail detail(Workspace workspace) {
        long memberCount = members.countByWorkspaceIdAndStatus(workspace.getId(), MemberStatus.ACTIVE);
        return new WorkspaceDetail(workspace, memberCount);
    }

    public record WorkspaceDetail(Workspace workspace, long memberCount) {
    }
}
