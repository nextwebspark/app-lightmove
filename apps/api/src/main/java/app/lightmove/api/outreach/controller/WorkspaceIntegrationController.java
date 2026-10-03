package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.dto.RecordAdminConsentRequest;
import app.lightmove.api.outreach.dto.UpdateWorkspaceIntegrationRequest;
import app.lightmove.api.outreach.dto.WorkspaceIntegrationsResponse;
import app.lightmove.api.outreach.model.OwnAppKeys;
import app.lightmove.api.outreach.service.WorkspaceIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings → Integrations, the caller's own workspace only. Reading it is as much an admin's as changing it. */
@RestController
@RequestMapping("/api/v1/workspace/integrations")
@RequiredArgsConstructor
public class WorkspaceIntegrationController {

    private final WorkspaceIntegrationService integrations;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceIntegrationsResponse list(@AuthenticationPrincipal AuthPrincipal principal) {
        return integrations.list(principal.requireWorkspaceId());
    }

    /** Sets the mode; {@code SHARED} here is the same as {@code DELETE}. */
    @PutMapping("/{provider}")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceIntegrationsResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable IntegrationProvider provider,
                                                @Valid @RequestBody UpdateWorkspaceIntegrationRequest request,
                                                HttpServletRequest httpRequest) {
        OwnAppKeys keys = new OwnAppKeys(request.clientId(), request.clientSecret(), request.tenantId(),
                request.secretExpiresOn());
        return integrations.update(principal.userId(), principal.requireWorkspaceId(), provider, request.mode(), keys,
                httpRequest);
    }

    /** Where the SPA reports Microsoft's admin-consent return; only Microsoft has one. */
    @PostMapping("/MICROSOFT/admin-consent")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceIntegrationsResponse recordMicrosoftAdminConsent(@AuthenticationPrincipal AuthPrincipal principal,
                                                                     @Valid @RequestBody RecordAdminConsentRequest request,
                                                                     HttpServletRequest httpRequest) {
        return integrations.recordMicrosoftAdminConsent(principal.userId(), principal.requireWorkspaceId(),
                request.tenantId(), httpRequest);
    }

    @DeleteMapping("/{provider}")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceIntegrationsResponse returnToSharedApp(@AuthenticationPrincipal AuthPrincipal principal,
                                                           @PathVariable IntegrationProvider provider,
                                                           HttpServletRequest httpRequest) {
        return integrations.useSharedApp(principal.userId(), principal.requireWorkspaceId(), provider, httpRequest);
    }
}
