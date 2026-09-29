package app.lightmove.api.enrichment.sourcing.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSourcingRunResponse;
import app.lightmove.api.enrichment.sourcing.dto.StartExecutiveSourcingRequest;
import app.lightmove.api.enrichment.sourcing.service.ExecutiveSourcingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Find executives: start a run, poll it, read the latest. All three are WORK_EXECUTE — the run
 * spends money and writes rows, and its picks and reasons rank people, which a client seat never sees.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/executive-sourcing")
@RequiredArgsConstructor
public class ExecutiveSourcingController {

    private final ExecutiveSourcingService sourcing;

    @PostMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<ExecutiveSourcingRunResponse> start(@AuthenticationPrincipal AuthPrincipal principal,
                                                              @PathVariable UUID projectId,
                                                              @Valid @RequestBody StartExecutiveSourcingRequest request,
                                                              HttpServletRequest httpRequest) {
        ExecutiveSourcingRunResponse run = sourcing.request(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(run);
    }

    @GetMapping("/latest")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<ExecutiveSourcingRunResponse> latest(@AuthenticationPrincipal AuthPrincipal principal,
                                                               @PathVariable UUID projectId) {
        return sourcing.latestOf(principal.requireWorkspaceId(), projectId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{runId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ExecutiveSourcingRunResponse status(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID projectId, @PathVariable UUID runId) {
        return sourcing.statusOf(principal.requireWorkspaceId(), projectId, runId);
    }
}
