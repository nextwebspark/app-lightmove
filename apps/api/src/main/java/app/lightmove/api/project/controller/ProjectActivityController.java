package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.project.dto.ProjectActivityResponse;
import app.lightmove.api.project.service.ProjectActivityService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A mandate's recent activity.
 *
 * <p>{@code WORK_EXECUTE} rather than the {@code WORK_VIEW} every other project read uses, and that
 * is the point: a client representative holds {@code WORK_VIEW}, and this feed narrates the firm's
 * own research — who contacted whom, what was imported, which companies were declined. The contact
 * lookup is gated the same way for the same reason. The drawer leaves the section out for a
 * representative rather than asking and being refused.
 */
@RestController
@RequiredArgsConstructor
public class ProjectActivityController {

    private final ProjectActivityService activity;

    @GetMapping("/api/v1/projects/{projectId}/activity")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<List<ProjectActivityResponse>> recent(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId) {
        return ResponseEntity.ok(activity.recent(principal.requireWorkspaceId(), projectId));
    }
}
