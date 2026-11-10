package app.lightmove.api.retention.controller;

import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.retention.dto.CandidateRetentionResponse;
import app.lightmove.api.retention.dto.RetentionDueResponse;
import app.lightmove.api.retention.dto.RetentionEraseRequest;
import app.lightmove.api.retention.dto.RetentionEraseResponse;
import app.lightmove.api.retention.dto.RetentionKeepResponse;
import app.lightmove.api.retention.dto.RetentionPeopleRequest;
import app.lightmove.api.retention.dto.UpdateCandidateRetentionRequest;
import app.lightmove.api.retention.service.CandidateRetentionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Data & retention's candidate half, an admin's: the period, who is past it, and keeping or deleting
 * them. Deleting is {@code PERSON_ERASE}, the right any erasure needs.
 */
@RestController
@RequestMapping("/api/v1/workspace/retention")
@RequiredArgsConstructor
@ConditionalOnProperty(name = DataProtectionSettings.SWITCH, havingValue = "true")
public class CandidateRetentionController {

    private final CandidateRetentionService retention;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public CandidateRetentionResponse period(@AuthenticationPrincipal AuthPrincipal principal) {
        return retention.period(principal.requireWorkspaceId());
    }

    @PutMapping
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public CandidateRetentionResponse changePeriod(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @RequestBody UpdateCandidateRetentionRequest request,
                                                   HttpServletRequest httpRequest) {
        return retention.changePeriod(principal.userId(), principal.requireWorkspaceId(), request.months(),
                httpRequest);
    }

    @GetMapping("/due")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public RetentionDueResponse due(@AuthenticationPrincipal AuthPrincipal principal,
                                    @RequestParam(defaultValue = "200") @Min(1)
                                    @Max(CandidateRetentionService.MAX_PAGE) int limit,
                                    @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return retention.due(principal.requireWorkspaceId(), limit, offset);
    }

    @PostMapping("/due/keep")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public RetentionKeepResponse keep(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody RetentionPeopleRequest request,
                                      HttpServletRequest httpRequest) {
        return retention.keep(principal.userId(), principal.requireWorkspaceId(), request.personIds(), httpRequest);
    }

    @PostMapping("/due/erase")
    @RequireWorkspacePermission(WorkspaceAction.PERSON_ERASE)
    public RetentionEraseResponse erase(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody RetentionEraseRequest request,
                                        HttpServletRequest httpRequest) {
        return retention.erase(principal.userId(), principal.requireWorkspaceId(), request.personIds(), httpRequest);
    }
}
