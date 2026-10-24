package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.outreach.dto.MyAvailabilityResponse;
import app.lightmove.api.outreach.dto.UpdateAvailabilityRequest;
import app.lightmove.api.outreach.dto.WorkspaceAvailabilityResponse;
import app.lightmove.api.outreach.service.AvailabilityService;
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
 * Working hours and the daily email cap: the workspace's default, an admin's to change, and each consultant's own.
 * Staff only — a client seat keeps no calendar and sends no email.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availability;

    @GetMapping("/workspace/availability")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public WorkspaceAvailabilityResponse workspace(@AuthenticationPrincipal AuthPrincipal principal) {
        return availability.workspace(principal.requireWorkspaceId());
    }

    @PutMapping("/workspace/availability")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceAvailabilityResponse changeWorkspace(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @Valid @RequestBody UpdateAvailabilityRequest request,
                                                         HttpServletRequest httpRequest) {
        return availability.changeWorkspace(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @GetMapping("/me/availability")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public MyAvailabilityResponse mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return availability.mine(principal.requireWorkspaceId(), principal.userId());
    }

    @PutMapping("/me/availability")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public MyAvailabilityResponse changeMine(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Valid @RequestBody UpdateAvailabilityRequest request,
                                             HttpServletRequest httpRequest) {
        return availability.changeMine(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }
}
