package app.lightmove.api.account.service;

import java.util.UUID;

/**
 * A feature holding rows that are the person's own within a workspace — their inbox, their settings, their
 * connections — which go with their account. Declared here and implemented where the rows live, so {@code account}
 * depends on none of them. What they did for the workspace (notes, tasks, positions) is the workspace's and stays.
 */
public interface AccountDeletionContributor {

    /** Inside the deletion's transaction and {@code workspaceId}'s scope, the membership already ended. */
    default void deleteAccountRowsIn(UUID workspaceId, UUID userId) {
    }

    /**
     * Once the deletion has committed, inside {@code workspaceId}'s scope: what is held at a provider, let go there.
     * Writes in transactions of its own ({@code REQUIRES_NEW}) — this may run on the deleting thread after its commit,
     * where a joined transaction writes nothing. Must not throw for a workspace where the person holds nothing.
     */
    default void releaseAfterAccountDeleted(UUID workspaceId, UUID userId) {
    }
}
