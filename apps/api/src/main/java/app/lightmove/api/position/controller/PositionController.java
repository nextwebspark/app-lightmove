package app.lightmove.api.position.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.position.dto.ApplyPositionTemplateRequest;
import app.lightmove.api.position.dto.CompensationDto;
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
 * The position brief of one mandate: reads are seat-gated WORK_VIEW, every write (publishing included)
 * PROJECT_EDIT, one PUT per step, each answering with the whole brief. The workspace comes from the
 * principal, never the path.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/position")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService position;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public PositionResponse get(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable UUID projectId) {
        return position.get(principal.requireWorkspaceId(), projectId);
    }

    /** Deliberately not {@link #get}, which drafts and saves a missing brief on every page view. */
    @GetMapping("/compensation")
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public CompensationDto getCompensation(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId) {
        return position.compensationOf(principal.requireWorkspaceId(), projectId);
    }

    @PutMapping("/details")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse putDetails(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID projectId,
                                       @Valid @RequestBody PutPositionDetailsRequest request,
                                       HttpServletRequest httpRequest) {
        return position.putDetails(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest);
    }

    @PutMapping("/context")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse putContext(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID projectId,
                                       @Valid @RequestBody PutMandateContextRequest request,
                                       HttpServletRequest httpRequest) {
        return position.putContext(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest);
    }

    @PutMapping("/reporting")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse putReporting(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID projectId,
                                         @Valid @RequestBody PutReportingStructureRequest request,
                                         HttpServletRequest httpRequest) {
        return position.putReporting(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest);
    }

    @PutMapping("/compensation")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse putCompensation(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID projectId,
                                            @Valid @RequestBody PutCompensationRequest request,
                                            HttpServletRequest httpRequest) {
        return position.putCompensation(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest);
    }

    @PutMapping("/criteria")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse putCriteria(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID projectId,
                                        @Valid @RequestBody PutCriteriaRequest request,
                                        HttpServletRequest httpRequest) {
        return position.putCriteria(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest);
    }

    @PutMapping("/competencies")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse putCompetencies(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID projectId,
                                            @Valid @RequestBody PutCompetenciesRequest request,
                                            HttpServletRequest httpRequest) {
        return position.putCompetencies(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest);
    }

    @PostMapping("/template")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse applyTemplate(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID projectId,
                                          @Valid @RequestBody ApplyPositionTemplateRequest request,
                                          HttpServletRequest httpRequest) {
        return position.applyTemplate(principal.userId(), principal.requireWorkspaceId(),
                projectId, request.templateId(), httpRequest);
    }

    @PostMapping("/publish")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse publish(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable UUID projectId,
                                    HttpServletRequest httpRequest) {
        return position.publish(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest);
    }

    @DeleteMapping("/publish")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public PositionResponse withdrawPublication(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID projectId,
                                                HttpServletRequest httpRequest) {
        return position.withdrawPublication(
                principal.userId(), principal.requireWorkspaceId(), projectId, httpRequest);
    }
}
