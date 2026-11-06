package app.lightmove.api.core.tenant;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A signed-in caller whose workspace every transaction it opens is scoped to, row-level security included. */
public interface TenantPrincipal {

    /** Null for a session that is in no workspace yet; such a request reads no tenant rows at all. */
    @Nullable UUID workspaceId();
}
