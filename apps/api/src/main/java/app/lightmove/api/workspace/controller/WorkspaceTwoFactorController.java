package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.workspace.dto.WorkspaceTwoFactorResponse;
import app.lightmove.api.workspace.service.WorkspaceTwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Security for an admin, always the caller's own workspace. The requirement itself is set through
 * {@code PUT /workspace/require-mfa}, beside the workspace's other settings.
 */
@RestController
@RequiredArgsConstructor
public class WorkspaceTwoFactorController {

    private final WorkspaceTwoFactorService twoFactor;

    @GetMapping("/api/v1/workspace/two-factor")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceTwoFactorResponse overview(@AuthenticationPrincipal AuthPrincipal principal) {
        return twoFactor.overview(principal.userId(), principal.requireWorkspaceId());
    }

    @DeleteMapping("/api/v1/members/{memberId}/two-factor")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID memberId,
                      HttpServletRequest request) {
        twoFactor.reset(principal.userId(), principal.requireWorkspaceId(), memberId, request);
    }
}
