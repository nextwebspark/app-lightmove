package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.project.dto.SourcingDtos.SourcingResponse;
import app.lightmove.api.project.service.SourcingService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The companies matching one mandate's Strategy scope. Gated the same as {@code StrategyController}
 * (WORK_EXECUTE, held by every project role) — sourcing results reveal the team's chosen scope just as
 * directly as the scope itself, so they get the same team-only gate rather than the workspace-level
 * PROJECT_BROWSE that CompanyReferenceController uses for caller-parameterised reads.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/sourcing")
@RequiredArgsConstructor
public class SourcingController {

    private final SourcingService sourcing;

    @GetMapping
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<SourcingResponse> get(@PathVariable UUID projectId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "25") int size) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(sourcing.get(principal.requireWorkspaceId(), projectId, page, size));
    }
}
