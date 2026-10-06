package app.lightmove.api.core.security.apikey;

import app.lightmove.api.common.constant.ApiValueEnum;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;

/** Who a public API request is: the key, and through it the workspace every read is scoped to. */
public record ApiKeyPrincipal(
        UUID keyId,
        String keyName,
        ApiKeyKind kind,
        UUID workspaceId,
        @Nullable UUID ownerUserId,
        List<ApiKeyScope> scopes,
        Instant expiresAt
) implements OAuth2AuthenticatedPrincipal {

    /** Empty for a key carrying a scope this build no longer knows, which is refused like any other bad key. */
    static Optional<ApiKeyPrincipal> of(ApiKey key) {
        List<ApiKeyScope> scopes = key.getScopes().stream()
                .map(token -> ApiValueEnum.fromValue(ApiKeyScope.class, token))
                .toList();
        if (scopes.contains(null)) {
            return Optional.empty();
        }
        return Optional.of(new ApiKeyPrincipal(key.getId(), key.getName(), key.getKind(), key.getWorkspaceId(),
                key.getOwnerUserId(), scopes, key.getExpiresAt()));
    }

    public boolean holds(ApiKeyScope scope) {
        return scopes.contains(scope);
    }

    @Override
    public String getName() {
        return keyId.toString();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return scopes.stream().map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.value())).toList();
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(OAuth2TokenIntrospectionClaimNames.SUB, getName());
        attributes.put(OAuth2TokenIntrospectionClaimNames.SCOPE, scopes.stream().map(ApiKeyScope::value).toList());
        attributes.put(OAuth2TokenIntrospectionClaimNames.EXP, expiresAt);
        attributes.put("workspace_id", workspaceId.toString());
        attributes.put("kind", kind.name());
        return attributes;
    }
}
