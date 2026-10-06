package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.security.dto.OAuthGrantResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.oauth.OAuthGrantService;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Connected AI apps, behind the same door as API keys: every staff member's own connections, and the
 * workspace's whole list or anyone else's under {@code WORKSPACE_MANAGE}, which the service checks.
 */
@RestController
@RequestMapping("/api/v1/workspace/oauth-grants")
@RequiredArgsConstructor
public class OAuthGrantController {

    private final OAuthGrantService grants;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.API_KEY_MANAGE)
    public List<OAuthGrantResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam(defaultValue = "false") boolean all) {
        return grants.list(principal.userId(), principal.requireWorkspaceId(), all);
    }

    @DeleteMapping("/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWorkspacePermission(WorkspaceAction.API_KEY_MANAGE)
    public void revoke(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID grantId,
                       HttpServletRequest httpRequest) {
        grants.revoke(principal.userId(), principal.requireWorkspaceId(), grantId, httpRequest);
    }
}
