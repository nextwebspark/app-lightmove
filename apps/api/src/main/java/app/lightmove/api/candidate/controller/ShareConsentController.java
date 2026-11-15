package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.ShareConsentRequestRequest;
import app.lightmove.api.candidate.dto.ShareConsentRowResponse;
import app.lightmove.api.candidate.dto.ShareConsentsResponse;
import app.lightmove.api.candidate.service.PersonConsentService;
import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A position's share-with-client consents (#1267 phase 3). {@code WORK_EXECUTE}, never {@code WORK_VIEW}: whether
 * someone agreed to be shown to the client is the firm's working fact, and a client seat holds {@code WORK_VIEW}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
@ConditionalOnProperty(name = DataProtectionSettings.SWITCH, havingValue = "true")
public class ShareConsentController {

    private final PersonConsentService consents;

    @GetMapping("/share-consents")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ShareConsentsResponse list(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId) {
        return consents.shareConsentsOf(principal.requireWorkspaceId(), projectId);
    }

    @PostMapping("/candidates/{candidateId}/share-consent-request")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ShareConsentRowResponse request(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                           @RequestBody(required = false) ShareConsentRequestRequest request,
                                           HttpServletRequest httpRequest) {
        return consents.requestShare(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                request == null ? null : request.nameClient(), httpRequest);
    }
}
