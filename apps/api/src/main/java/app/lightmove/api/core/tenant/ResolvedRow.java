package app.lightmove.api.core.tenant;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A row a door's key named, and the workspace that holds it — null only for an OAuth grant awaiting consent. */
public record ResolvedRow(UUID id, @Nullable UUID workspaceId) {
}
