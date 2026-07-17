package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.project.dto.PositionDtos.PositionResponse;
import app.lightmove.api.project.dto.PositionDtos.PutCompetenciesRequest;
import app.lightmove.api.project.dto.PositionDtos.PutCriteriaRequest;
import app.lightmove.api.project.dto.PositionDtos.UpdatePositionRequest;
import app.lightmove.api.project.service.PositionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The position brief of one mandate. Reading rides the workspace-level PROJECT_BROWSE (whoever sees
 * the project list may read its brief); every write is PROJECT_EDIT on the seat, except unlocking —
 * a locked brief is the downstream benchmark, so reopening it is the ADMIN-only POSITION_UNLOCK.
 * The workspace comes from the principal, never the path.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/position")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService position;

    @GetMapping
    @PreAuthorize("@workspaceAuth.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<PositionResponse> get(@PathVariable UUID projectId) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(position.get(principal.requireWorkspaceId(), projectId));
    }

    @PutMapping
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> update(@PathVariable UUID projectId,
                                                   @Valid @RequestBody UpdatePositionRequest request,
                                                   HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(position.update(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/criteria")
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> putCriteria(@PathVariable UUID projectId,
                                                        @Valid @RequestBody PutCriteriaRequest request,
                                                        HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(position.putCriteria(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/competencies")
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> putCompetencies(@PathVariable UUID projectId,
                                                            @Valid @RequestBody PutCompetenciesRequest request,
                                                            HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(position.putCompetencies(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PostMapping("/lock")
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<PositionResponse> lock(@PathVariable UUID projectId,
                                                 HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(position.lock(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }

    @PostMapping("/unlock")
    @PreAuthorize("@projectAuth.can(principal, #projectId, 'POSITION_UNLOCK')")
    public ResponseEntity<PositionResponse> unlock(@PathVariable UUID projectId,
                                                   HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(position.unlock(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest));
    }
}
