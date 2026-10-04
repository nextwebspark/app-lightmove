package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.outreach.dto.CandidateOutreachResponse;
import app.lightmove.api.outreach.dto.OutreachOverviewResponse;
import app.lightmove.api.outreach.service.OutreachEnrollmentService;
import app.lightmove.api.outreach.service.OutreachMonitorService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** People in outreach on a position: the page's counts and table, one executive's run, and Stop. {@code WORK_EXECUTE}. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/outreach")
@RequiredArgsConstructor
public class OutreachRunController {

    private final OutreachMonitorService monitor;
    private final OutreachEnrollmentService enrollments;

    @GetMapping("/people")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public OutreachOverviewResponse overview(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID projectId) {
        return monitor.overview(principal.requireWorkspaceId(), projectId);
    }

    @GetMapping("/candidates/{candidateId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CandidateOutreachResponse ofCandidate(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable UUID projectId, @PathVariable UUID candidateId) {
        return monitor.ofCandidate(principal.requireWorkspaceId(), projectId, candidateId);
    }

    @PostMapping("/enrollments/{enrollmentId}/stop")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                     @PathVariable UUID enrollmentId, HttpServletRequest httpRequest) {
        enrollments.stop(principal.userId(), principal.requireWorkspaceId(), projectId, enrollmentId, httpRequest);
    }
}
