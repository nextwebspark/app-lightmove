package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.AddSuppressionRequest;
import app.lightmove.api.candidate.dto.SuppressionResponse;
import app.lightmove.api.candidate.model.SuppressionAddition;
import app.lightmove.api.candidate.service.DataProtectionControls;
import app.lightmove.api.candidate.service.SuppressionListService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The workspace's suppression list, an admin's: read, add an entry, lift one. */
@RestController
@RequestMapping("/api/v1/workspace/suppressions")
@RequiredArgsConstructor
public class SuppressionController {

    private final SuppressionListService suppressions;
    private final DataProtectionControls controls;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public List<SuppressionResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        controls.requireOffered();
        return suppressions.list(principal.requireWorkspaceId());
    }

    /** 201 when anything was new; 200 when every key was already listed, and the entries holding them. */
    @PostMapping
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public ResponseEntity<List<SuppressionResponse>> add(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @Valid @RequestBody AddSuppressionRequest request,
                                                         HttpServletRequest httpRequest) {
        controls.requireOffered();
        SuppressionAddition addition = suppressions.add(principal.userId(), principal.requireWorkspaceId(), request,
                httpRequest);
        return ResponseEntity.status(addition.added() > 0 ? HttpStatus.CREATED : HttpStatus.OK)
                .body(addition.entries());
    }

    @DeleteMapping("/{suppressionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public void remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID suppressionId,
                       HttpServletRequest httpRequest) {
        controls.requireOffered();
        suppressions.remove(principal.userId(), principal.requireWorkspaceId(), suppressionId, httpRequest);
    }
}
