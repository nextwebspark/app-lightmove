package app.lightmove.api.positiontemplate.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.positiontemplate.dto.PositionTemplateSummary;
import app.lightmove.api.positiontemplate.service.PositionTemplateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * <p>Editing lives elsewhere: the library on {@link PositionTemplateLibraryController}, a firm's own
 * templates on {@link WorkspacePositionTemplateController}.
 */
@RestController
@RequestMapping("/api/v1/position-templates")
@RequiredArgsConstructor
public class PositionTemplateController {

    private final PositionTemplateService templates;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public List<PositionTemplateSummary> list(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return templates.list(principal.requireWorkspaceId());
    }
}
