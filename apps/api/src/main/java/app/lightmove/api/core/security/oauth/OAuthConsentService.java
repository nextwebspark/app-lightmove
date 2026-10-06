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
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The two reads the consent screen makes around the authorization server's own endpoints: what is being asked, before
 * Allow; and, once the request is stored, the {@code state} its consent is posted with. Neither grants anything — the
 * framework decides at the authorize endpoint, under {@link McpAuthorizationRules}.
 */
@Service
public class OAuthConsentService {

    private static final OAuth2TokenType STATE = new OAuth2TokenType(OAuth2ParameterNames.STATE);

    private final OAuthClientJpaRepository clients;
    private final HashingAuthorizationService authorizations;
    private final WorkspaceSelection selection;
    private final WorkspaceRepository workspaces;
    private final WorkspaceAccess access;
    private final RegisteredClientRepository registeredClients;
    private final ClientVerification verification;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    public OAuthConsentService(OAuthClientJpaRepository clients, HashingAuthorizationService authorizations,
                               WorkspaceSelection selection, WorkspaceRepository workspaces, WorkspaceAccess access,
                               RegisteredClientRepository registeredClients, ClientVerification verification,
                               PlatformTransactionManager transactions, Clock clock) {
        this.clients = clients;
        this.authorizations = authorizations;
        this.selection = selection;
        this.workspaces = workspaces;
        this.access = access;
        this.registeredClients = registeredClients;
        this.verification = verification;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    /**
     * Only a client already on file, so this read never makes the server fetch a URL a caller typed; a metadata
     * document's copy that expired since is refreshed first, outside the transaction.
     */
    public OAuthConsentContextResponse context(UUID userId, String clientId, String redirectUri, String scope) {
        OAuthClient onFile = clientId == null ? null : clients.findByClientId(clientId).orElse(null);
        if (onFile == null) {
            throw ApiException.of(ErrorCode.OAUTH_CLIENT_NOT_FOUND);
        }
        if (onFile.getSource() == OAuthClientSource.CIMD && !onFile.getMetadataExpiresAt().isAfter(clock.instant())) {
            if (registeredClients.findByClientId(clientId) == null) {
                throw ApiException.of(ErrorCode.OAUTH_CLIENT_NOT_FOUND);
            }
            onFile = clients.findByClientId(clientId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.OAUTH_CLIENT_NOT_FOUND));
        }
        OAuthClient client = onFile;
        return readOnly.execute(status -> contextOf(userId, client, redirectUri, scope));
    }

    private OAuthConsentContextResponse contextOf(UUID userId, OAuthClient client, String redirectUri, String scope) {
        String redirect = redirectUri != null ? redirectUri
                : client.getRedirectUris().size() == 1 ? client.getRedirectUris().getFirst() : null;
        if (redirect == null || !RedirectUriRules.matches(client.getRedirectUris(), redirect)) {
            throw ApiException.of(ErrorCode.OAUTH_CLIENT_NOT_FOUND);
        }

        // Set.copyOf, not Set.of: a repeated scope is a legal request, and Set.of throws on it.
        Set<String> asked = scope == null ? Set.of() : Set.copyOf(Arrays.asList(scope.trim().split("\\s+")));
        List<String> requested = ApiKeyScope.dataScopes().stream().map(ApiKeyScope::value)
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

        return new OAuthConsentContextResponse(client.getClientId(), client.getClientName(), client.getSource(),
                ClientVerification.documentHostOf(client.getSource(), client.getClientId()),
                verification.isVerified(client.getSource(), client.getClientId()), client.getClientUri(),
                client.getLogoUri(), OAuthUris.hostOf(redirect), requested, offered);
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
        List<String> requested = ApiKeyScope.dataScopes().stream().map(ApiKeyScope::value)
                .filter(request.getScopes()::contains)
                .toList();
        return new OAuthPendingConsentResponse(client.getClientId(), state, requested,
                HashingAuthorizationService.workspaceOf(authorization).orElse(null));
    }
}
