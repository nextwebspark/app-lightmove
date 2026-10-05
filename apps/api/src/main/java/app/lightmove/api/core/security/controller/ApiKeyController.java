package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.security.apikey.ApiKeyService;
import app.lightmove.api.core.security.dto.ApiKeyResponse;
import app.lightmove.api.core.security.dto.CreateApiKeyRequest;
import app.lightmove.api.core.security.dto.CreatedApiKeyResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → API keys. Every staff member manages their own personal keys; a workspace key, the whole
 * workspace's list and anyone else's key also ask {@code WORKSPACE_MANAGE}, which the service checks.
 */
@RestController
@RequestMapping("/api/v1/workspace/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeys;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.API_KEY_MANAGE)
    public List<ApiKeyResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                     @RequestParam(defaultValue = "false") boolean all) {
        return apiKeys.list(principal.userId(), principal.requireWorkspaceId(), all);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.API_KEY_MANAGE)
    public CreatedApiKeyResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody CreateApiKeyRequest request,
                                        HttpServletRequest httpRequest) {
        return apiKeys.create(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWorkspacePermission(WorkspaceAction.API_KEY_MANAGE)
    public void revoke(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID keyId,
                       HttpServletRequest httpRequest) {
        apiKeys.revoke(principal.userId(), principal.requireWorkspaceId(), keyId, httpRequest);
    }
}
