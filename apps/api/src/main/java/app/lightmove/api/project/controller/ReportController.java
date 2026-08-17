package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.project.dto.ReportResponse;
import app.lightmove.api.project.service.ReportService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One mandate's report. Seat-gated on WORK_VIEW like the rest of a project's content: the report
 * restates the strategy's scope in aggregate, so it can be no more public than the scope itself — and
 * the same gate is what lets an attached client representative read it from their own seat, which is
 * the whole point of a report the client is meant to see.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reports;

    @GetMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<ReportResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID projectId) {
        return ResponseEntity.ok(reports.get(principal.requireWorkspaceId(), projectId));
    }
}
