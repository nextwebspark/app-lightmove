package app.lightmove.api.core.security.oauth;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Workspace names by id, for the consent screen; the workspace module answers, which owns the rows. */
public interface WorkspaceNames {

    /** Only the workspaces that exist and are readable here; an id with no row is simply absent. */
    Map<UUID, String> namesOf(Collection<UUID> workspaceIds);
}
