package app.lightmove.api.position.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.position.dto.PositionTemplateSummary;
import app.lightmove.api.position.service.PositionTemplateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The role templates a workspace can draft a brief from.
 *
 * <p>Workspace-scoped rather than project-scoped, and gated {@code PROJECT_BROWSE} like the company
 * reference reads: the catalog is the firm's own library plus LightMove's, and which mandate is open
 * when somebody browses it says nothing about who may see it. Applying one <i>is</i> project-scoped,
 * and lives on the brief's own controller under the seat gate every other write carries.
 *
 * <p>No write path yet. Managing templates is a migration today (V42), and the screen that will let a
 * firm edit its own is a later session — the rows it will write already exist, keyed to the workspace.
 */
@RestController
@RequestMapping("/api/v1/position-templates")
@RequiredArgsConstructor
public class PositionTemplateController {

    private final PositionTemplateService templates;

    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<List<PositionTemplateSummary>> list(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(templates.list(principal.requireWorkspaceId()));
    }
}
