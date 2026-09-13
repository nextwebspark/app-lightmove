package app.lightmove.api.report.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.report.dto.ReportResponse;
import app.lightmove.api.report.service.ReportService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<ReportResponse> read(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID projectId) {
        return ResponseEntity.ok(reports.read(principal.requireWorkspaceId(), projectId));
    }
}
