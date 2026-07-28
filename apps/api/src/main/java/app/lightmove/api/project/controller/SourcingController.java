package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.project.dto.SourcingDtos.SourcingResponse;
import app.lightmove.api.project.dto.SourcingDtos.SourcingRunResponse;
import app.lightmove.api.project.service.SourcingRunService;
import app.lightmove.api.project.service.SourcingService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The companies matching one mandate's Strategy scope. Reads are gated WORK_VIEW, held by every
 * seated role — sourcing results reveal the team's chosen scope just as directly as the scope
 * itself, so they get the same team-only gate rather than the workspace-level PROJECT_BROWSE that
 * CompanyReferenceController uses for caller-parameterised reads.
 *
 * <p>The {@code /runs} routes are the CoreSignal POC flow. Starting or extending a run spends
 * metered third-party credits — a write in every way that matters — so those two carry
 * WORK_EXECUTE, while polling stays WORK_VIEW like every other read. The legacy local-table
 * {@code GET /sourcing} stays untouched alongside for review-time comparison.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/sourcing")
@RequiredArgsConstructor
public class SourcingController {

    private final SourcingService sourcing;
    private final SourcingRunService sourcingRuns;

    @GetMapping
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<SourcingResponse> get(@PathVariable UUID projectId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "25") int size) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(sourcing.get(principal.requireWorkspaceId(), projectId, page, size));
    }

    /** Start the CoreSignal run for the current strategy — or return the one that already answers it. */
    @PostMapping("/runs")
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<SourcingRunResponse> startRun(@PathVariable UUID projectId) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(sourcingRuns.start(principal.requireWorkspaceId(), projectId));
    }

    /** The poll: run state plus every company collected so far, in fixed revenue-desc order. */
    @GetMapping("/runs/current")
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<SourcingRunResponse> currentRun(@PathVariable UUID projectId) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(sourcingRuns.current(principal.requireWorkspaceId(), projectId));
    }

    /** Collect the next batch of the search results ("load more" on scroll). */
    @PostMapping("/runs/current/extend")
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<SourcingRunResponse> extendRun(@PathVariable UUID projectId) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(sourcingRuns.extend(principal.requireWorkspaceId(), projectId));
    }
}
