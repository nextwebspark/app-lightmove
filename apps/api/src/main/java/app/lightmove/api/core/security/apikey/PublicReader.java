package app.lightmove.api.core.security.apikey;

import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Whoever reads through the public surface, an API key or an MCP connection alike: the workspace every read is scoped
 * to, the user whose live permissions bound it — none for a workspace key, which reads its whole workspace — and the
 * scopes that shape what each row carries.
 */
public record PublicReader(UUID workspaceId, @Nullable UUID userId, Set<ApiKeyScope> scopes) {

    public static PublicReader of(ApiKeyPrincipal key) {
        return new PublicReader(key.workspaceId(), key.kind() == ApiKeyKind.SERVICE ? null : key.ownerUserId(),
                Set.copyOf(key.scopes()));
    }

    public boolean holds(ApiKeyScope scope) {
        return scopes.contains(scope);
    }

    public boolean readsWholeWorkspace() {
        return userId == null;
    }
}
