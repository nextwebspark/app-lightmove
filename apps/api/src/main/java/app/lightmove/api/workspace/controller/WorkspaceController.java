package app.lightmove.api.workspace.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.workspace.dto.WorkspaceDtos.DeleteWorkspaceRequest;
import app.lightmove.api.workspace.dto.WorkspaceDtos.UpdateWorkspaceSettingsRequest;
import app.lightmove.api.workspace.dto.WorkspaceDtos.WorkspaceResponse;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.service.WorkspaceSettingsService;
import app.lightmove.api.workspace.service.WorkspaceSettingsService.WorkspaceDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → General. The workspace is always the caller's own — no id in the path. Tier gating is
 * declared here: reading is a staff matter, changing it is the WORKSPACE_MANAGE action.
 */
@RestController
@RequestMapping("/api/v1/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceSettingsService settings;

    @GetMapping
    @PreAuthorize("@workspaceAuth.staff(principal)")
    public ResponseEntity<WorkspaceResponse> get() {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(toResponse(
                settings.get(principal.requireWorkspaceId())));
    }

    @PatchMapping
    @PreAuthorize("@workspaceAuth.can(principal, 'WORKSPACE_MANAGE')")
    public ResponseEntity<WorkspaceResponse> update(@Valid @RequestBody UpdateWorkspaceSettingsRequest request,
                                                    HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(toResponse(settings.update(
                principal.userId(), principal.requireWorkspaceId(),
                request.name(), request.defaultRegion(), request.defaultCurrency(), httpRequest)));
    }

    @DeleteMapping
    @PreAuthorize("@workspaceAuth.can(principal, 'WORKSPACE_MANAGE')")
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteWorkspaceRequest request,
                                       HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        settings.delete(principal.userId(), principal.requireWorkspaceId(),
                request.confirmName(), httpRequest);
        return ResponseEntity.noContent().build();
    }

    private WorkspaceResponse toResponse(WorkspaceDetail detail) {
        Workspace ws = detail.workspace();
        return new WorkspaceResponse(ws.getId(), ws.getName(), ws.getSlug(), ws.getLogoMark(),
                ws.getEmailDomain(), ws.getDefaultRegion(), ws.getDefaultCurrency(), ws.getPlan(),
                detail.memberCount(), ws.getCreatedAt());
    }
}
