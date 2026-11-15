package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.BulkConsentRequestRequest;
import app.lightmove.api.candidate.dto.BulkConsentRequestResponse;
import app.lightmove.api.candidate.dto.ConsentRequestRequest;
import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.dto.RecordConsentRequest;
import app.lightmove.api.candidate.service.PersonConsentService;
import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Asking a person for consent, and recording an answer they gave another way (#1267). */
@RestController
@RequestMapping("/api/v1/candidates")
@RequiredArgsConstructor
@ConditionalOnProperty(name = DataProtectionSettings.SWITCH, havingValue = "true")
public class PersonConsentController {

    private final PersonConsentService consents;

    @PostMapping("/{personId}/consent-requests")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse request(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                                        @Valid @RequestBody ConsentRequestRequest request,
                                        HttpServletRequest httpRequest) {
        return consents.request(principal.userId(), principal.requireWorkspaceId(), personId, request.purposes(),
                httpRequest);
    }

    @PostMapping("/bulk/consent-requests")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public BulkConsentRequestResponse requestEach(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @Valid @RequestBody BulkConsentRequestRequest request,
                                                  HttpServletRequest httpRequest) {
        return consents.requestEach(principal.userId(), principal.requireWorkspaceId(), request.personIds(),
                request.purposes(), httpRequest);
    }

    @PostMapping("/{personId}/consents/recorded")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse record(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                                       @Valid @RequestBody RecordConsentRequest request,
                                       HttpServletRequest httpRequest) {
        return consents.recordByHand(principal.userId(), principal.requireWorkspaceId(), personId, request,
                httpRequest);
    }
}
