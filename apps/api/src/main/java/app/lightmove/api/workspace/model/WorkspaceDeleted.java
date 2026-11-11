package app.lightmove.api.workspace.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A workspace was deleted: its members are out, and everything it holds goes at the purge. Published inside the
 * deleting transaction, so what stops with it — runs, scheduled emails — commits or rolls back with the deletion.
 */
public record WorkspaceDeleted(UUID workspaceId, UUID actorUserId, Instant deletedAt) {
}
