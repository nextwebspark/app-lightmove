package app.lightmove.api.mcp.service;

import app.lightmove.api.core.security.apikey.ApiKeyPrincipal;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.oauth.OAuthGrantService;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.mcp.constant.McpCredentialKind;
import app.lightmove.api.mcp.model.McpCaller;
import app.lightmove.api.mcp.model.McpCallerAuthentication;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;

/**
 * Turns either credential into one {@link McpCaller}, re-reading what can change under a token on every call: an OAuth
 * token's grant and its user's staff access, as a personal key's owner is re-read by its introspector.
 */
@Service
@RequiredArgsConstructor
public class McpCredentials {

    private final OAuthGrantService grants;
    private final WorkspaceAccess access;

    public McpCallerAuthentication fromAccessToken(Jwt token) {
        Optional<UUID> userId = uuidOf(token.getSubject());
        Optional<UUID> workspaceId = uuidOf(token.getClaimAsString("wsId"));
        Optional<UUID> grantId = uuidOf(token.getClaimAsString("grant_id"));
        if (userId.isEmpty() || workspaceId.isEmpty() || grantId.isEmpty()) {
            throw refused("The token names no grant");
        }
        if (!grants.isLive(grantId.get(), userId.get(), workspaceId.get())
                || !access.holdsAction(userId.get(), workspaceId.get(), WorkspaceAction.API_KEY_MANAGE)) {
            throw refused("The connection has ended");
        }
        return new McpCallerAuthentication(new McpCaller(McpCredentialKind.OAUTH, userId.get(), workspaceId.get(),
                scopesOf(token.getClaim("scope")), token.getClaimAsString("client_id"), grantId.get()));
    }

    /** A key reaches the MCP server only once its owner opted it in. */
    public McpCallerAuthentication fromApiKey(ApiKeyPrincipal key) {
        if (!key.holds(ApiKeyScope.MCP_USE)) {
            throw refused("This key is not enabled for MCP");
        }
        return new McpCallerAuthentication(new McpCaller(McpCredentialKind.API_KEY, key.ownerUserId(),
                key.workspaceId(), dataScopesAmong(key::holds), null, key.keyId()));
    }

    /** The granted scopes, as an array or a space-separated string; anything this build does not know is dropped. */
    private static Set<ApiKeyScope> scopesOf(Object claim) {
        Collection<?> tokens = claim instanceof Collection<?> list ? list
                : claim instanceof String text ? Arrays.asList(text.trim().split("\\s+"))
                : Set.of();
        Set<String> granted = tokens.stream().map(String::valueOf).collect(Collectors.toSet());
        return dataScopesAmong(scope -> granted.contains(scope.value()));
    }

    /** What a caller may read: never {@code mcp:use}, which opens the server and reads nothing. */
    private static Set<ApiKeyScope> dataScopesAmong(Predicate<ApiKeyScope> held) {
        return ApiKeyScope.dataScopes().stream().filter(held).collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<UUID> uuidOf(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private static InvalidBearerTokenException refused(String reason) {
        return new InvalidBearerTokenException(reason);
    }
}
