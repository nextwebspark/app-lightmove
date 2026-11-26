package app.lightmove.api.subprocessor.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.subprocessor.dto.ObjectToSubprocessorRequest;
import app.lightmove.api.subprocessor.dto.WorkspaceSubprocessorNoticeResponse;
import app.lightmove.api.subprocessor.service.SubprocessorObjectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings → Data & retention's Sub-processor changes, an admin's: what Uncava announced, and the objection to one. */
@RestController
@RequestMapping("/api/v1/workspace/subprocessor-notices")
@RequiredArgsConstructor
public class WorkspaceSubprocessorNoticeController {

    private final SubprocessorObjectionService objections;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public List<WorkspaceSubprocessorNoticeResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return objections.list(principal.requireWorkspaceId());
    }

    @PutMapping("/{noticeId}/objection")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceSubprocessorNoticeResponse object(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @PathVariable UUID noticeId,
                                                      @Valid @RequestBody ObjectToSubprocessorRequest request,
                                                      HttpServletRequest httpRequest) {
        return objections.object(principal.userId(), principal.requireWorkspaceId(), noticeId, request, httpRequest);
    }
}
