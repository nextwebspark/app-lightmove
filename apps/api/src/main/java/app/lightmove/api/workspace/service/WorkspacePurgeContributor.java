package app.lightmove.api.workspace.service;

import java.util.UUID;

/**
 * Work a feature must do before a deleted workspace's rows go, where it holds something outside the database the rows
 * are the only way back to — a recording at Recall, a grant at a mail provider. Declared here, implemented where the
 * rows live, so {@code workspace} depends on none of them. Called in the workspace's scope and outside any
 * transaction, on every attempt of a purge, so it must be safe to repeat; what it cannot release it logs and leaves,
 * because the purge goes on regardless.
 */
public interface WorkspacePurgeContributor {

    void beforePurge(UUID workspaceId);
}
