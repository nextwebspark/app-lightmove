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
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public PositionResponse get(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable UUID projectId) {
        return position.get(principal.requireWorkspaceId(), projectId);
    }

    /**
     * What the brief pays, for a screen that wants one figure off it — the executive drawer, which
     * offers the mandate's currency to a new person. Deliberately not {@link #get}: that drafts and
     * saves a brief for a mandate that has none, and a grid nobody asked for a brief on would then
     * write a row on every page view.
     */
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

    /**
     * Draft this brief as a different role. PROJECT_EDIT like every other write — it replaces the
     * drafted half of the brief, and the picker that calls it sits inside the wizard.
     */
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
