package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.project.dto.PositionResponse;
import app.lightmove.api.project.dto.PutCompetenciesRequest;
import app.lightmove.api.project.dto.PutCriteriaRequest;
import app.lightmove.api.project.dto.UpdatePositionRequest;
import app.lightmove.api.project.service.PositionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The position brief of one mandate. Reading needs a seat on the project (WORK_EXECUTE, which every
 * project role holds), with the workspace-admin bypass so an admin sees every project — a brief is
 * team content, not browsable to the whole workspace. Every write is PROJECT_EDIT on the seat, except
 * unlocking — a locked brief is the downstream benchmark, so reopening it is the LEAD-only
 * POSITION_UNLOCK. The workspace comes from the principal, never the path.
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

    @PutMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID projectId,
                                                   @Valid @RequestBody UpdatePositionRequest request,
                                                   HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.update(
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

    @PostMapping("/lock")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> lock(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable UUID projectId,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.lock(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }

    @PostMapping("/unlock")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'POSITION_UNLOCK')")
    public ResponseEntity<PositionResponse> unlock(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID projectId,
                                                   HttpServletRequest httpRequest) {
        return ResponseEntity.ok(position.unlock(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }
}
