package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.outreach.dto.CreateEmailTemplateRequest;
import app.lightmove.api.outreach.dto.EmailTemplateResponse;
import app.lightmove.api.outreach.dto.EmailTemplateSharingRequest;
import app.lightmove.api.outreach.dto.EmailTemplatesResponse;
import app.lightmove.api.outreach.dto.UpdateEmailTemplateRequest;
import app.lightmove.api.outreach.service.EmailTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Email templates and the composer's picker: the caller's own templates and the workspace's shared ones.
 * Staff only — a pure client sends no email; who may change a shared one is the service's to decide.
 */
@RestController
@RequiredArgsConstructor
public class EmailTemplateController {

    private final EmailTemplateService templates;

    @GetMapping("/api/v1/me/email-templates")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public EmailTemplatesResponse list(@AuthenticationPrincipal AuthPrincipal principal) {
        return templates.list(principal.userId(), principal.requireWorkspaceId());
    }

    @PostMapping("/api/v1/me/email-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public EmailTemplateResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody CreateEmailTemplateRequest request,
                                        HttpServletRequest httpRequest) {
        return templates.create(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @PutMapping("/api/v1/me/email-templates/{templateId}")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public EmailTemplateResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID templateId,
                                        @Valid @RequestBody UpdateEmailTemplateRequest request,
                                        HttpServletRequest httpRequest) {
        return templates.update(principal.userId(), principal.requireWorkspaceId(), templateId, request, httpRequest);
    }

    @PutMapping("/api/v1/me/email-templates/{templateId}/sharing")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public EmailTemplateResponse setSharing(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID templateId,
                                            @Valid @RequestBody EmailTemplateSharingRequest request,
                                            HttpServletRequest httpRequest) {
        return templates.setSharing(principal.userId(), principal.requireWorkspaceId(), templateId, request,
                httpRequest);
    }

    @PostMapping("/api/v1/me/email-templates/{templateId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public EmailTemplateResponse duplicate(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID templateId) {
        return templates.duplicate(principal.userId(), principal.requireWorkspaceId(), templateId);
    }

    @DeleteMapping("/api/v1/me/email-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID templateId,
                       @RequestParam long version, HttpServletRequest httpRequest) {
        templates.delete(principal.userId(), principal.requireWorkspaceId(), templateId, version, httpRequest);
    }
}
