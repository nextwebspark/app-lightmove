package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.workspace.dto.WorkspacePurgeCertificateResponse;
import app.lightmove.api.workspace.dto.WorkspaceRestoredResponse;
import app.lightmove.api.workspace.service.WorkspaceLifecycleService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A platform admin restoring a deleted workspace, or reading the certificate its purge left. The admin is signed in
 * to their own workspace; the one the path names is resolved, then scoped.
 */
@RestController
@RequiredArgsConstructor
public class PlatformWorkspaceLifecycleController {

    private final WorkspaceLifecycleService lifecycle;

    @PostMapping("/api/v1/platform/workspaces/{workspaceId}/restore")
    @RequirePlatformPermission(PlatformAction.WORKSPACE_LIFECYCLE_MANAGE)
    public WorkspaceRestoredResponse restore(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID workspaceId, HttpServletRequest httpRequest) {
        int members = WorkspaceScope.call(workspaceId, () -> lifecycle.restore(principal.userId(), workspaceId,
                httpRequest));
        return new WorkspaceRestoredResponse(workspaceId, members);
    }

    @GetMapping("/api/v1/platform/workspaces/{workspaceId}/purge-certificate")
    @RequirePlatformPermission(PlatformAction.WORKSPACE_LIFECYCLE_MANAGE)
    public WorkspacePurgeCertificateResponse certificate(@PathVariable UUID workspaceId) {
        return WorkspaceScope.call(workspaceId,
                () -> WorkspacePurgeCertificateResponse.of(lifecycle.certificate(workspaceId)));
    }
}
