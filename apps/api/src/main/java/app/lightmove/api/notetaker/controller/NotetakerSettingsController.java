package app.lightmove.api.notetaker.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.notetaker.dto.ChangeNotetakerRequest;
import app.lightmove.api.notetaker.dto.MyNotetakerResponse;
import app.lightmove.api.notetaker.dto.WorkspaceNotetakerResponse;
import app.lightmove.api.notetaker.service.NotetakerSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Notetaker's two standing switches: the workspace's (Settings → Integrations, its admin's to change) and
 * the caller's own (Settings → Profile). Staff only; a pure client is not told the Notetaker exists.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotetakerSettingsController {

    private final NotetakerSettingsService settings;

    @GetMapping("/workspace/notetaker")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public WorkspaceNotetakerResponse workspace(@AuthenticationPrincipal AuthPrincipal principal) {
        return settings.workspace(principal.requireWorkspaceId());
    }

    @PutMapping("/workspace/notetaker")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceNotetakerResponse changeWorkspace(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @Valid @RequestBody ChangeNotetakerRequest request,
                                                      HttpServletRequest httpRequest) {
        return settings.changeWorkspace(principal.userId(), principal.requireWorkspaceId(), request.enabled(),
                httpRequest);
    }

    @GetMapping("/me/notetaker")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public MyNotetakerResponse mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return settings.mine(principal.userId(), principal.requireWorkspaceId());
    }

    @PutMapping("/me/notetaker")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public MyNotetakerResponse changeMine(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody ChangeNotetakerRequest request,
                                          HttpServletRequest httpRequest) {
        return settings.changeMine(principal.userId(), principal.requireWorkspaceId(), request.enabled(), httpRequest);
    }
}
