package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.CandidatePrivacySettingsResponse;
import app.lightmove.api.candidate.dto.UpdateCandidatePrivacySettingsRequest;
import app.lightmove.api.candidate.service.CandidatePrivacySettingsService;
import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings → Data & retention → Candidate privacy (#923): read by anyone who sends a notice, changed by an admin. */
@RestController
@RequestMapping("/api/v1/workspace/candidate-privacy")
@RequiredArgsConstructor
@ConditionalOnProperty(name = DataProtectionSettings.SWITCH, havingValue = "true")
public class CandidatePrivacySettingsController {

    private final CandidatePrivacySettingsService settings;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public CandidatePrivacySettingsResponse settings(@AuthenticationPrincipal AuthPrincipal principal) {
        return settings.responseOf(principal.requireWorkspaceId());
    }

    @PutMapping
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public CandidatePrivacySettingsResponse change(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @Valid @RequestBody UpdateCandidatePrivacySettingsRequest request,
                                                   HttpServletRequest httpRequest) {
        return settings.change(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }
}
