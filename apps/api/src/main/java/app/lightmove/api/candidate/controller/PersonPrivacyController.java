package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.BulkPrivacyNoticeRequest;
import app.lightmove.api.candidate.dto.BulkPrivacyNoticeResponse;
import app.lightmove.api.candidate.dto.LawfulBasisRequest;
import app.lightmove.api.candidate.dto.MarkNotifiedRequest;
import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.service.PersonPrivacyService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A person's lawful basis and privacy notice (#923), from the Candidates drawer and its selection bar. */
@RestController
@RequestMapping("/api/v1/candidates")
@RequiredArgsConstructor
@ConditionalOnProperty(name = DataProtectionSettings.SWITCH, havingValue = "true")
public class PersonPrivacyController {

    private final PersonPrivacyService privacy;

    @PutMapping("/{personId}/lawful-basis")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse lawfulBasis(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID personId, @RequestBody LawfulBasisRequest request,
                                            HttpServletRequest httpRequest) {
        return privacy.changeLawfulBasis(principal.userId(), principal.requireWorkspaceId(), personId,
                request.lawfulBasis(), httpRequest);
    }

    @PostMapping("/{personId}/privacy-notice")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse sendNotice(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID personId, HttpServletRequest httpRequest) {
        return privacy.sendNotice(principal.userId(), principal.requireWorkspaceId(), personId, httpRequest);
    }

    @PostMapping("/{personId}/privacy-notice/marked")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse markNotified(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID personId,
                                             @Valid @RequestBody MarkNotifiedRequest request,
                                             HttpServletRequest httpRequest) {
        return privacy.markNotified(principal.userId(), principal.requireWorkspaceId(), personId,
                request.notifiedOn(), httpRequest);
    }

    @PostMapping("/bulk/privacy-notice")
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public BulkPrivacyNoticeResponse sendNotices(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody BulkPrivacyNoticeRequest request,
                                                 HttpServletRequest httpRequest) {
        return privacy.sendNotices(principal.userId(), principal.requireWorkspaceId(), request.personIds(),
                httpRequest);
    }
}
