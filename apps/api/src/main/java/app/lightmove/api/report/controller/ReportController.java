package app.lightmove.api.report.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.report.dto.ReportResponse;
import app.lightmove.api.report.dto.TeamPerformanceDto;
import app.lightmove.api.report.service.ReportService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The report's one read, gated {@code WORK_VIEW} like the grids it summarises: a client representative
 * who may read a mandate's companies and people may read what they add up to.
 */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reports;

    @GetMapping("/api/v1/projects/{projectId}/report")
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public ReportResponse read(@AuthenticationPrincipal AuthPrincipal principal,
                               @PathVariable UUID projectId) {
        return reports.read(principal.requireWorkspaceId(), projectId);
    }

    /**
     * Researcher performance — {@code WORK_EXECUTE}, not {@code WORK_VIEW}: it ranks the firm's own
     * staff, which is the firm's business and not the client's, however much of the mandate they may read.
     */
    @GetMapping("/api/v1/projects/{projectId}/report/team")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public TeamPerformanceDto readTeam(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID projectId,
                                       @RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to) {
        return reports.readTeam(principal.requireWorkspaceId(), projectId, from, to);
    }
}
