package app.lightmove.api.workspace.service;

import java.util.UUID;

/**
 * Work a feature must do before a deleted workspace's rows go, where it holds something outside the database the rows
 * are the only way back to — a recording at Recall, a grant at a mail provider. Declared here, implemented where the
 * rows live, so {@code workspace} depends on none of them. Called in the workspace's scope and outside any
 * transaction, on every attempt of a purge, so it must be safe to repeat. What it cannot release yet it throws for, and
 * the purge waits and tries again, up to {@link WorkspacePurge#RELEASE_PATIENCE} after it first started.
 */
public interface WorkspacePurgeContributor {

    void beforePurge(UUID workspaceId);
}
