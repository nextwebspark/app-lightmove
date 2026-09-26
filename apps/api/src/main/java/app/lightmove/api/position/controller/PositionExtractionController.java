package app.lightmove.api.position.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.position.dto.PositionExtractionResponse;
import app.lightmove.api.position.service.PositionExtractionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Read from document" on the brief's steps. Gated {@code PROJECT_EDIT}, not {@code WORK_VIEW}: it
 * spends a billed model call a read-only client seat must not run up, and is never a side effect of
 * upload or of reading the brief.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/position/document/extract")
@RequiredArgsConstructor
public class PositionExtractionController {

    private final PositionExtractionService extraction;

    @PostMapping("/details")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionExtractionResponse extractDetails(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
            HttpServletRequest httpRequest) {
        return extraction.extractDetails(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest);
    }

    @PostMapping("/context")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionExtractionResponse extractContext(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
            HttpServletRequest httpRequest) {
        return extraction.extractContext(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest);
    }

    @PostMapping("/assessment")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionExtractionResponse extractAssessment(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
            HttpServletRequest httpRequest) {
        return extraction.extractAssessment(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest);
    }

    @PostMapping("/reporting")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionExtractionResponse extractReporting(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
            HttpServletRequest httpRequest) {
        return extraction.extractReporting(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest);
    }
}
