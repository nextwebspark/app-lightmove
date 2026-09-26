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
 * The role templates a workspace can draft a brief from — workspace-scoped and gated
 * {@code PROJECT_BROWSE}; applying one is the brief's own seat-gated write.
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
