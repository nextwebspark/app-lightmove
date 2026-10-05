package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.dto.OAuthConsentContextResponse;
import app.lightmove.api.core.security.dto.OAuthConsentWorkspace;
import app.lightmove.api.core.security.dto.OAuthPendingConsentResponse;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.service.WorkspaceSelection;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two reads the consent screen makes around the authorization server's own endpoints: what is being asked, before
 * Allow; and, once the request is stored, the {@code state} its consent is posted with. Neither grants anything — the
 * framework decides at the authorize endpoint, under {@link McpAuthorizationRules}.
 */
@Service
@RequiredArgsConstructor
public class OAuthConsentService {

    private static final OAuth2TokenType STATE = new OAuth2TokenType(OAuth2ParameterNames.STATE);

    private final OAuthClientJpaRepository clients;
    private final HashingAuthorizationService authorizations;
    private final WorkspaceSelection selection;
    private final WorkspaceRepository workspaces;
    private final WorkspaceAccess access;

    @Transactional(readOnly = true)
    public OAuthConsentContextResponse context(UUID userId, String clientId, String redirectUri, String scope) {
        OAuthClient client = clients.findByClientId(clientId)
                .orElseThrow(() -> ApiException.of(ErrorCode.OAUTH_CLIENT_NOT_FOUND));
        String redirect = redirectUri != null ? redirectUri
                : client.getRedirectUris().size() == 1 ? client.getRedirectUris().getFirst() : null;
        if (redirect == null || !client.getRedirectUris().contains(redirect)) {
            throw ApiException.of(ErrorCode.OAUTH_CLIENT_NOT_FOUND);
        }

        Set<String> asked = scope == null ? Set.of() : Set.of(scope.trim().split("\\s+"));
        List<String> requested = Arrays.stream(ApiKeyScope.values()).map(ApiKeyScope::value)
                .filter(asked::contains)
                .filter(client.getScopes()::contains)
                .toList();

        List<WorkspaceMember> memberships = selection.all(userId);
        Map<UUID, Workspace> byId = workspaces
                .findAllById(memberships.stream().map(WorkspaceMember::getWorkspaceId).toList()).stream()
                .collect(Collectors.toMap(Workspace::getId, Function.identity()));
        List<OAuthConsentWorkspace> offered = memberships.stream()
                .filter(member -> byId.containsKey(member.getWorkspaceId()))
                .map(member -> new OAuthConsentWorkspace(member.getWorkspaceId(),
                        byId.get(member.getWorkspaceId()).getName(),
                        access.holdsAction(userId, member.getWorkspaceId(), WorkspaceAction.API_KEY_MANAGE)))
                .toList();

        return new OAuthConsentContextResponse(client.getClientId(), client.getClientName(), client.getClientUri(),
                client.getLogoUri(), OAuthGrantService.hostOf(redirect), requested, offered);
    }

    /** Only the caller's own request, for the client it names: a state is not a capability to read another's. */
    @Transactional(readOnly = true)
    public OAuthPendingConsentResponse pending(UUID userId, String clientId, String state) {
        OAuth2Authorization authorization = state == null ? null : authorizations.findByToken(state, STATE);
        OAuthClient client = clients.findByClientId(clientId).orElse(null);
        if (authorization == null || client == null
                || !authorization.getRegisteredClientId().equals(client.getId().toString())
                || !authorization.getPrincipalName().equals(userId.toString())) {
            throw ApiException.of(ErrorCode.OAUTH_REQUEST_NOT_FOUND);
        }
        OAuth2AuthorizationRequest request = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        List<String> requested = Arrays.stream(ApiKeyScope.values()).map(ApiKeyScope::value)
                .filter(request.getScopes()::contains)
                .toList();
        return new OAuthPendingConsentResponse(client.getClientId(), state, requested,
                HashingAuthorizationService.workspaceOf(authorization).orElse(null));
    }
}
