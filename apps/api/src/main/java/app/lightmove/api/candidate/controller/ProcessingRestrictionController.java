package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.PersonRecordResponse;
import app.lightmove.api.candidate.dto.ProcessingRestrictionRequest;
import app.lightmove.api.candidate.service.ProcessingRestrictionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Restricting processing of a person (#920, Art. 18), and lifting it. */
@RestController
@RequestMapping("/api/v1/candidates/{personId}/processing-restriction")
@RequiredArgsConstructor
@ConditionalOnProperty(name = DataProtectionSettings.SWITCH, havingValue = "true")
public class ProcessingRestrictionController {

    private final ProcessingRestrictionService restrictions;

    @PutMapping
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public PersonRecordResponse processingRestriction(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @PathVariable UUID personId,
                                                      @Valid @RequestBody ProcessingRestrictionRequest request,
                                                      HttpServletRequest httpRequest) {
        return restrictions.restrictProcessing(principal.userId(), principal.requireWorkspaceId(), personId, request,
                httpRequest);
    }
}
