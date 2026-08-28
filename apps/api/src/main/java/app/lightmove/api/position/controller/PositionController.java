package app.lightmove.api.position.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.position.dto.PositionResponse;
import app.lightmove.api.position.dto.PutCompensationRequest;
import app.lightmove.api.position.dto.PutCompetenciesRequest;
import app.lightmove.api.position.dto.PutCriteriaRequest;
import app.lightmove.api.position.dto.PutMandateContextRequest;
import app.lightmove.api.position.dto.PutPositionDetailsRequest;
import app.lightmove.api.position.dto.PutReportingStructureRequest;
import app.lightmove.api.position.service.PositionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The position brief of one mandate. Reading needs a seat on the project (WORK_VIEW, which every
 * project role holds), with the workspace-admin bypass so an admin sees every project — a brief is
 * team content, not browsable to the whole workspace. Every write is PROJECT_EDIT on the seat. The
 * workspace comes from the principal, never the path.
 *
 * <p>One PUT per wizard step rather than one for the whole document: the screen autosaves the step in
 * front of the consultant, and every one of these answers with the whole brief so nothing has to be
 * merged client-side.
 *
 * <p>Publishing is gated PROJECT_EDIT like any other write, and needs no action of its own — V38
 * deleted {@code POSITION_UNLOCK} on purpose, and a {@code POSITION_PUBLISH} beside it would rebuild
 * the same gate under a friendlier name.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/position")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService position;

    @GetMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<PositionResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID projectId) {
        return ResponseEntity.ok(position.get(principal.requireWorkspaceId(), projectId));
    }

    @PutMapping("/details")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> putDetails(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable UUID projectId,
                                                       @Valid @RequestBody PutPositionDetailsRequest request,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.putDetails(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/context")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> putContext(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable UUID projectId,
                                                       @Valid @RequestBody PutMandateContextRequest request,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.putContext(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/reporting")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> putReporting(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID projectId,
                                                         @Valid @RequestBody PutReportingStructureRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.putReporting(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/compensation")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> putCompensation(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @PathVariable UUID projectId,
                                                            @Valid @RequestBody PutCompensationRequest request,
                                                            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.putCompensation(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/criteria")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> putCriteria(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable UUID projectId,
                                                        @Valid @RequestBody PutCriteriaRequest request,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.putCriteria(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/competencies")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> putCompetencies(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @PathVariable UUID projectId,
                                                            @Valid @RequestBody PutCompetenciesRequest request,
                                                            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.putCompetencies(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PostMapping("/publish")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> publish(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable UUID projectId,
                                                    HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.publish(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }

    @DeleteMapping("/publish")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> withdrawPublication(@AuthenticationPrincipal AuthPrincipal principal,
                                                                @PathVariable UUID projectId,
                                                                HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.withdrawPublication(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }
}
